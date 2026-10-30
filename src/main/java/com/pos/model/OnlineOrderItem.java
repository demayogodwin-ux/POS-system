package com.pos.model;

import java.math.BigDecimal;

public record OnlineOrderItem(int productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
}
