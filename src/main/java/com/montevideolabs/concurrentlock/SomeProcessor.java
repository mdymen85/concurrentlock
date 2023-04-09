package com.montevideolabs.concurrentlock;

import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.locks.ReadWriteLock;

@Component
public class SomeProcessor {

    private final LockProvider locks;

    public SomeProcessor(LockProvider lockProvider) {
        this.locks = lockProvider;
    }

    void process(UserEvent event) {
        String userId = event.getUserId();
        var userLock = (ReadWriteLock) locks.get(userId);
        synchronized (userLock) {
            this.method(event);
        }
    }

    void method(UserEvent event) {
        event.setMoney(event.getMoney() + 1);
    }
}




