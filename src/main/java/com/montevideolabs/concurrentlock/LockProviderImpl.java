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

    private final static int MAX_UNUSED_LOCKS = 10000;
    private final static long CLEANUP_INTERVAL = 1; // seconds
    private final static int TIMEOUT = 1; //seconds
    private final static int CLEANUP_THRESHOLD = MAX_UNUSED_LOCKS / 2;




    private LockProviderImpl() {
        log.info("Create scheduled thread pool for doing clean up of unused locks by an interval of {}.", CLEANUP_INTERVAL);
        ScheduledExecutorService executorService = newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(this::cleanupUnusedLocksIfNeeded, CLEANUP_INTERVAL, CLEANUP_INTERVAL,
                TimeUnit.SECONDS);
    }

    @Override
    public ReadWriteLock get(final String lockId) {
        Lock lockRef = locks.computeIfAbsent(lockId, id -> new Lock(new ReentrantReadWriteLock()));
        lockRef.incrementCount();

        log.info("Incrementing lock : {} to value : {}.", lockId, lockRef.getCount());
        var now = Instant.now();

        log.info("Creating new element in queue to remove locks : {} at : {}.", lockId, now);
        locksToRemove.add(new LockToRemove(lockId, now));

        log.info("Locks to be removed {}", locksToRemove.size());
        return lockRef.getLock();
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
        if (locksToRemove.size() > CLEANUP_THRESHOLD) {
            cleanupUnusedLocks();
        }
    }

    /**
     * Get the oldest element in the queue locksToRemove and check if
     * its expired. If so: decrement from the lock, and if its the
     * last lock alive in the queue, will remove it from the concurrent map
     */
    private void cleanupUnusedLocks() {
        LockToRemove lockToRemove;
        var needToRemove = true;
        while (((lockToRemove = locksToRemove.peek()) != null) && (needToRemove) ) {
            log.info("Checking if lock : {} need to be removed.", lockToRemove.getLockId());
            Lock lockRef = locks.get(lockToRemove.getLockId());
            Instant now = Instant.now();
            needToRemove = now.minus(TIMEOUT, ChronoUnit.SECONDS).isAfter(lockToRemove.getLockInstant());

            if (needToRemove) {
                log.info("Lock : {} is old, will be removed from queue.", lockToRemove.getLockId());
                locksToRemove.poll();
            }

            if (needToRemove && lockRef.decrementCount()) {
                log.info("Removing lock : {} from map.", lockToRemove);
                locks.remove(lockToRemove.getLockId());
            }

            log.info("Lock : {} has count of : {}.", lockToRemove, lockRef.getCount());
        }
    }

    private record LockToRemove(String lockId, Instant lockInstant) {

        private String getLockId() {
            return lockId;
        }

        private Instant getLockInstant() {
            return this.lockInstant;
        }

    }

    private static class Lock {
        private final ReadWriteLock lock;
        private final LongAdder count;

        Lock(ReadWriteLock lock) {
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

