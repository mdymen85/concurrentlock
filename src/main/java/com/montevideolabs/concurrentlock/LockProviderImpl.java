package com.montevideolabs.concurrentlock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.Executors.newScheduledThreadPool;

@Slf4j
@Component
public class LockProviderImpl implements LockProvider {

    // in order to control concurrency in a thread-safe structure, I use conccurentHashMap.
    private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();
    // in order to control the oldest element to remove from the concurrent map, I put the locked string
    // in a queue.
    private final ConcurrentLinkedQueue<LockToRemove> locksToRemove = new ConcurrentLinkedQueue<>();
    private static final int MAX_UNUSED_LOCKS = 10000;
    private static final long CLEANUP_INTERVAL = 1; // seconds
    private static final int TIMEOUT = 1; //seconds
    private static final int CLEANUP_THRESHOLD = MAX_UNUSED_LOCKS / 2;

    private LockProviderImpl() {
        try {
            startCleanupLocks();
        } catch (Exception e) {
            log.error("An error occurred while starting cleanup locks: {}", e.getMessage(), e);
            throw new LockProviderException("Unable to start cleanup locks", e);
        }
    }

    private void startCleanupLocks() {
        try {
            log.info("Create scheduled thread pool for doing clean up of unused locks by an interval of {}.", CLEANUP_INTERVAL);
            ScheduledExecutorService executorService = newScheduledThreadPool(1);
            executorService.scheduleAtFixedRate(this::cleanupUnusedLocksIfNeeded, CLEANUP_INTERVAL, CLEANUP_INTERVAL,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("An error occurred while creating the scheduled thread pool: {}", e.getMessage(), e);
            throw new LockProviderException("Unable to create scheduled thread pool", e);
        }
    }

    @Override
    public ReadWriteLock get(final String lockId) {
        try {
            final Lock lockRef = locks.computeIfAbsent(lockId, id -> new Lock(new ReentrantReadWriteLock()));
            incrementCount(lockId, lockRef);
            addLockToRemove(lockId);
            return lockRef.getLock();
        } catch (Exception e) {
            log.error("An error occurred while getting the lock: {}", e.getMessage(), e);
            throw new LockProviderException("Unable to get the lock", e);
        }
    }

    private static void incrementCount(final String lockId, final Lock lockRef) {
        lockRef.incrementCount();
        log.info("Incrementing lock : {} to value : {}.", lockId, lockRef.getCount());
    }

    private void addLockToRemove(final String lockId) {
        locksToRemove.add(new LockToRemove(lockId));
        log.info("Locks to be removed {}", locksToRemove.size());
    }

    /**
     * Just for testing porpuse.
     * Return a specific lock.
     *
     * @param lockId
     * @return
     */
    public Lock checkLock(final String lockId) {
        return locks.get(lockId);
    }

    private void cleanupUnusedLocksIfNeeded() {
        try {
            if (locksToRemove.size() > CLEANUP_THRESHOLD) {
                cleanupUnusedLocks();
            }
        } catch (Exception e) {
            log.error("An error occurred while cleaning up unused locks: {}", e.getMessage(), e);
            throw new LockProviderException("Unable to cleanup unused locks", e);
        }
    }

    /**
     * Get the oldest element in the queue locksToRemove and check if
     * its expired. If so: decrement from the lock, and if its the
     * last lock alive in the queue, will remove it from the concurrent map
     */
    private void cleanupUnusedLocks() {
        LockToRemove lockToRemove;
        try {
            while ((lockToRemove = locksToRemove.peek()) != null) {
                log.info("Checking if lock : {} need to be removed.", lockToRemove.getLockId());
                if (lockToRemove.canRemoveLock()) {
                    removeLockFromQueue(lockToRemove);
                    removeLockFromMap(lockToRemove);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("An error occurred while cleaning up unused locks: {}", e.getMessage(), e);
            throw new LockProviderException("Unable to cleanup unused locks", e);
        }
    }

    private void removeLockFromQueue(final LockToRemove lockToRemove) {
        log.info("Lock : {} is old, will be removed from queue.", lockToRemove.getLockId());
        locksToRemove.poll();
    }

    private void removeLockFromMap(final LockToRemove lockToRemove) {
        final Lock lock = locks.get(lockToRemove.getLockId());
        if (lock == null) {
            log.warn("Lock : {} not found in map.", lockToRemove.getLockId());
            return;
        }
        try {
            if (lock.decrementCount()) {
                log.info("Removing lock : {} from map.", lockToRemove);
                locks.remove(lockToRemove.getLockId());
            }
            log.info("Lock : {} has count of : {}.", lockToRemove, lock.getCount());
        } catch (Exception e) {
            log.error("Error while removing lock from map: {}", e.getMessage());
        }
    }


    private record LockToRemove(String lockId, Instant lockInstant) {
        public LockToRemove(final String lockId) {
            this(lockId, Instant.now());
        }

        private String getLockId() {
            return lockId;
        }

        private Instant getLockInstant() {
            return lockInstant;
        }

        private boolean canRemoveLock() {
            final Instant now = Instant.now();
            return now.minus(TIMEOUT, ChronoUnit.SECONDS).isAfter(this.getLockInstant());
        }
    }

    private static class Lock {
        private final ReadWriteLock lock;
        private final LongAdder count;

        Lock(final ReadWriteLock lock) {
            this.lock = lock;
            this.count = new LongAdder();
        }

        ReadWriteLock getLock() {
            return lock;
        }

        void incrementCount() {
            count.increment();
        }

        boolean decrementCount() {
            count.decrement();
            return count.sum() == 0;
        }

        private long getCount() {
            return count.longValue();
        }
    }
}