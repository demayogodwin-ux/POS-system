package com.pos.db;

import java.sql.SQLException;

public class InsufficientStockException extends SQLException {
    private final String productName;

    public InsufficientStockException(String productName) {
        super("Not enough stock for " + productName);
        this.productName = productName;
    }

    public String getProductName() {
        return this.productName;
    }
}
