package com.montevideolabs.concurrentlock;

public interface LockProvider {
    /**
     * Get a lock with a given ID.
     *
     * @param lockId the ID of the lock to get.
     * @return lock for the given ID.
     */
    Object get(String lockId);
}