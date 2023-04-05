package com.montevideolabs.concurrentlock;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class LockProviderImpl implements LockProvider {

    private final ConcurrentHashMap<String, LockReference> locks = new ConcurrentHashMap<>();

    @Override
    public Object get(String lockId) {
        LockReference lockRef = locks.compute(lockId, (id, ref) -> {
            if (ref == null) {
                ref = new LockReference(lockId);
            }
            ref.incrementCount();
            return ref;
        });
        return lockRef.getLock();
    }

    private static class LockReference {
        private final String lock;
        private int count;

        LockReference(String lock) {
            this.lock = lock;
            this.count = 0;
        }

        Object getLock() {
            return lock;
        }

        void incrementCount() {
            count++;
        }

        boolean decrementCount() {
            count--;
            return count == 0;
        }
    }

    // Periodically remove unreferenced locks to avoid long-term memory saturation
    private static final long CLEANUP_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes
    private static final int MAX_UNUSED_LOCKS = 10000;

    public LockProviderImpl() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                cleanupUnusedLocks();
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private void cleanupUnusedLocks() {
        int numLocksRemoved = 0;
        for (String lockId : locks.keySet()) {
            LockReference lockRef = locks.get(lockId);
            if (lockRef.decrementCount()) {
                locks.remove(lockId, lockRef);
                numLocksRemoved++;
                if (numLocksRemoved >= MAX_UNUSED_LOCKS) {
                    break;
                }
            }
        }
    }
}

