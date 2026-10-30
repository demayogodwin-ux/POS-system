package com.pos.model;

public enum PaymentMethod {
    CASH("Cash"),
    CARD("Card"),
    EWALLET("E-wallet");

    private final String label;

    private PaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }
}
