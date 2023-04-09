package com.montevideolabs.concurrentlock;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.Executors.newScheduledThreadPool;

@Component
public class LockProviderImpl implements LockProvider {

    private final ConcurrentHashMap<String, LockReference> locks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> locksToRemove = new ConcurrentLinkedQueue<>();
    private static final int MAX_UNUSED_LOCKS = 10000;
    private static final long CLEANUP_INTERVAL = 1; // seconds
    private static final int CLEANUP_THRESHOLD = MAX_UNUSED_LOCKS / 2;
    private final LongAdder locksRemovedCounter = new LongAdder();
    private static final int TIMEOUT = 1; //seconds

    private LockProviderImpl() {
        ScheduledExecutorService executorService = newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(this::cleanupUnusedLocksIfNeeded, CLEANUP_INTERVAL, CLEANUP_INTERVAL,
                TimeUnit.SECONDS);
    }

    @Override
    public ReadWriteLock get(final String lockId) {
        LockReference lockRef = locks.computeIfAbsent(lockId, id -> new LockReference(new ReentrantReadWriteLock(), locksToRemove));
        lockRef.incrementCount();
        lockRef.locksToRemove.add(lockId);
        lockRef.lockInstant = Instant.now();
        return lockRef.getLock();
    }

    public LockReference checkLock(final String lockId) {
        return locks.get(lockId);
    }


    private void cleanupUnusedLocksIfNeeded() {
        if (locksToRemove.size() > CLEANUP_THRESHOLD) {
            cleanupUnusedLocks();
        }
    }

    private void cleanupUnusedLocks() {
        String lockId;
        var needToRemove = true;
        while (((lockId = locksToRemove.poll()) != null) && (needToRemove) ) {
            LockReference lockRef = locks.get(lockId);
            Instant now = Instant.now();
            needToRemove = now.minus(TIMEOUT, ChronoUnit.SECONDS).isBefore(lockRef.lockInstant);
            if (needToRemove && lockRef.decrementCount()) {
                locks.remove(lockId);
                locksRemovedCounter.increment();
            }
        }
        if (locksRemovedCounter.intValue() > 0) {
            Logger.getLogger(LockProviderImpl.class.getName()).log(Level.INFO, "Removed {0} unused locks", locksRemovedCounter.intValue());
        }
    }

    private static class LockReference {
        private final ReadWriteLock lock;
        private final LongAdder count;
        private final ConcurrentLinkedQueue<String> locksToRemove;
        private Instant lockInstant;

        LockReference(ReadWriteLock lock, ConcurrentLinkedQueue<String> locksToRemove) {
            this.lock = lock;
            this.count = new LongAdder();
            this.locksToRemove = locksToRemove;
            this.lockInstant = Instant.now();
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
    }
}

