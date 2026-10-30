package com.pos.model;

public class User {
    private int id;
    private String username;
    private String fullName;
    private Role role;
    private boolean active;

    public User() {
    }

    public User(int id, String username, String fullName, Role role, boolean active) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return this.fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return this.role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    @Override
    public String toString() {
        return this.fullName + " (" + this.username + ", " + this.role + ")";
    }
}
