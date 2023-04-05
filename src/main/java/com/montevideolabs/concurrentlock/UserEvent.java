package com.montevideolabs.concurrentlock;

public class UserEvent {

    private String userId;
    private Long money;

    public UserEvent(String userId) {
        this.userId = userId;
        this.money = 0L;
    }

    public Long getMoney() {
        return this.money;
    }

    public void setMoney(Long money) {
        this.money = money;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return this.userId;
    }


}
