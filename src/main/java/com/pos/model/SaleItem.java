package com.pos.model;

import com.pos.service.Money;
import java.math.BigDecimal;

public class SaleItem {
    private int id;
    private int saleId;
    private int productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice = Money.ZERO;
    private BigDecimal lineTotal = Money.ZERO;

    public SaleItem() {
    }

    public SaleItem(int id, int saleId, int productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.id = id;
        this.saleId = saleId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.setUnitPrice(unitPrice);
        this.setLineTotal(lineTotal);
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSaleId() {
        return this.saleId;
    }

    public void setSaleId(int saleId) {
        this.saleId = saleId;
    }

    public int getProductId() {
        return this.productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return this.productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return this.unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = Money.of(unitPrice);
    }

    public BigDecimal getLineTotal() {
        return this.lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = Money.of(lineTotal);
    }
}
