package com.pos.model;

/**
 * PENDING, then PREPARING, then READY (in store) or OUT_FOR_DELIVERY (delivery), then COMPLETED.
 * Any open order can be CANCELLED.
 */
public enum OrderStatus {
    PENDING("New"),
    PREPARING("Preparing"),
    READY("Ready"),
    OUT_FOR_DELIVERY("Out for delivery"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    private OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public boolean isOpen() {
        return this != COMPLETED && this != CANCELLED;
    }

    /** The step after this one, or null when staff cannot move it forward. */
    public OrderStatus next(OrderType type) {
        return switch (this) {
            case PENDING -> PREPARING;
            case PREPARING -> type == OrderType.DELIVERY ? OUT_FOR_DELIVERY : READY;
            case READY, OUT_FOR_DELIVERY -> COMPLETED;
            default -> null;
        };
    }
}
