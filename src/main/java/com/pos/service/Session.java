package com.pos.service;

import com.pos.model.User;
import java.time.LocalDateTime;

public final class Session {
    private static final Session INSTANCE = new Session();
    private User currentUser;
    private LocalDateTime loginTime;

    private Session() {
    }

    public static Session getInstance() {
        return INSTANCE;
    }

    public synchronized void start(User user) {
        this.currentUser = user;
        this.loginTime = LocalDateTime.now();
    }

    public synchronized void clear() {
        this.currentUser = null;
        this.loginTime = null;
    }

    public synchronized boolean isLoggedIn() {
        return this.currentUser != null;
    }

    public synchronized User getCurrentUser() {
        return this.currentUser;
    }

    public synchronized LocalDateTime getLoginTime() {
        return this.loginTime;
    }
}
