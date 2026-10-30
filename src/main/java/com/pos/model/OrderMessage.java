package com.pos.model;

import java.time.LocalDateTime;

/** One chat message about an online order, from the customer or from store staff. */
public record OrderMessage(int id, int orderId, boolean fromStaff, String staffName, String body, LocalDateTime createdAt) {
    public static final int MAX_LENGTH = 500;
}
