package com.pos.model;

public enum OrderType {
    DELIVERY("Delivery"),
    IN_STORE("In store");

    private final String label;

    private OrderType(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }
}
