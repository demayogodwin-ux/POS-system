package com.pos.model;

import java.math.BigDecimal;

public record DaySummary(BigDecimal totalSales, int saleCount, BigDecimal averageSale, long itemsSold, BigDecimal taxCollected) {
}
