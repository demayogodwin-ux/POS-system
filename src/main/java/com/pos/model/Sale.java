package com.pos.model;

import com.pos.service.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Sale {
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_VOID = "VOID";
    public static final String STATUS_REFUNDED = "REFUNDED";
    private int id;
    private String receiptNo;
    private int userId;
    private String cashierName;
    private LocalDateTime saleTime;
    private BigDecimal subtotal = Money.ZERO;
    private BigDecimal tax = Money.ZERO;
    private BigDecimal total = Money.ZERO;
    private PaymentMethod paymentMethod = PaymentMethod.CASH;
    private BigDecimal amountPaid = Money.ZERO;
    private BigDecimal changeDue = Money.ZERO;
    private String status = "PAID";
    private List<SaleItem> items = new ArrayList<>();
    private int itemQuantity;

    public int itemCount() {
        return this.items.isEmpty() ? this.itemQuantity : this.items.stream().mapToInt(SaleItem::getQuantity).sum();
    }

    public void setItemQuantity(int itemQuantity) {
        this.itemQuantity = itemQuantity;
    }

    public boolean isPaid() {
        return "PAID".equals(this.status);
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getReceiptNo() {
        return this.receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public int getUserId() {
        return this.userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getCashierName() {
        return this.cashierName;
    }

    public void setCashierName(String cashierName) {
        this.cashierName = cashierName;
    }

    public LocalDateTime getSaleTime() {
        return this.saleTime;
    }

    public void setSaleTime(LocalDateTime saleTime) {
        this.saleTime = saleTime;
    }

    public BigDecimal getSubtotal() {
        return this.subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = Money.of(subtotal);
    }

    public BigDecimal getTax() {
        return this.tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = Money.of(tax);
    }

    public BigDecimal getTotal() {
        return this.total;
    }

    public void setTotal(BigDecimal total) {
        this.total = Money.of(total);
    }

    public PaymentMethod getPaymentMethod() {
        return this.paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmountPaid() {
        return this.amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = Money.of(amountPaid);
    }

    public BigDecimal getChangeDue() {
        return this.changeDue;
    }

    public void setChangeDue(BigDecimal changeDue) {
        this.changeDue = Money.of(changeDue);
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<SaleItem> getItems() {
        return this.items;
    }

    public void setItems(List<SaleItem> items) {
        this.items = items;
    }
}
