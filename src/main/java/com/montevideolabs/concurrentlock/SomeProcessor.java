package com.montevideolabs.concurrentlock;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class SomeProcessor {

    private final LockProvider locks;
    public static int NUMBER = 0;

    public SomeProcessor(LockProvider lockProvider) {
        this.locks = lockProvider;
    }

    void process(UserEvent event) {
        String userId = event.getUserId();
        var userLock = (String)locks.get(userId);
        synchronized (userLock) {
            this.method(event);
        }
    }

    void method(UserEvent event) {
        event.setMoney(event.getMoney() + 1);
        NUMBER++;
    }
}




