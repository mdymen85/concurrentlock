package com.montevideolabs.concurrentlock;

public class LockProviderException extends RuntimeException{

    public LockProviderException(String message, Exception e) {
        super(message, e);
    }
}