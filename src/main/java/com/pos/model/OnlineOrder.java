package com.pos.model;

import com.pos.service.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OnlineOrder {
    private int id;
    private String orderNo;
    private int customerId;
    private String customerName;
    private String customerPhone;
    private int storeId;
    private String storeName;
    private OrderType type = OrderType.DELIVERY;
    private OrderStatus status = OrderStatus.PENDING;
    private PaymentMethod paymentMethod = PaymentMethod.CASH;
    private BigDecimal subtotal = Money.ZERO;
    private BigDecimal tax = Money.ZERO;
    private BigDecimal total = Money.ZERO;
    private String deliveryAddress;
    private BigDecimal deliveryLat;
    private BigDecimal deliveryLng;
    private String inStoreSpot;
    private String note;
    private String riderCode;
    private BigDecimal riderLat;
    private BigDecimal riderLng;
    private LocalDateTime riderUpdatedAt;
    private LocalDateTime dispatchedAt;
    private Integer saleId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OnlineOrderItem> items = new ArrayList<>();
    private int itemQuantity;
    // Chat messages the store has not read yet, and ones the customer has not read yet.
    private int unreadByStaff;
    private int unreadByCustomer;

    public int itemCount() {
        return this.items.isEmpty() ? this.itemQuantity : this.items.stream().mapToInt(OnlineOrderItem::quantity).sum();
    }

    public void setItemQuantity(int itemQuantity) {
        this.itemQuantity = itemQuantity;
    }

    public int getUnreadByStaff() {
        return this.unreadByStaff;
    }

    public void setUnreadByStaff(int unreadByStaff) {
        this.unreadByStaff = unreadByStaff;
    }

    public int getUnreadByCustomer() {
        return this.unreadByCustomer;
    }

    public void setUnreadByCustomer(int unreadByCustomer) {
        this.unreadByCustomer = unreadByCustomer;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getOrderNo() {
        return this.orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public int getCustomerId() {
        return this.customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return this.customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return this.customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public int getStoreId() {
        return this.storeId;
    }

    public void setStoreId(int storeId) {
        this.storeId = storeId;
    }

    public String getStoreName() {
        return this.storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public OrderType getType() {
        return this.type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public OrderStatus getStatus() {
        return this.status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public PaymentMethod getPaymentMethod() {
        return this.paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
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

    public String getDeliveryAddress() {
        return this.deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public BigDecimal getDeliveryLat() {
        return this.deliveryLat;
    }

    public void setDeliveryLat(BigDecimal deliveryLat) {
        this.deliveryLat = deliveryLat;
    }

    public BigDecimal getDeliveryLng() {
        return this.deliveryLng;
    }

    public void setDeliveryLng(BigDecimal deliveryLng) {
        this.deliveryLng = deliveryLng;
    }

    public String getInStoreSpot() {
        return this.inStoreSpot;
    }

    public void setInStoreSpot(String inStoreSpot) {
        this.inStoreSpot = inStoreSpot;
    }

    public String getNote() {
        return this.note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getRiderCode() {
        return this.riderCode;
    }

    public void setRiderCode(String riderCode) {
        this.riderCode = riderCode;
    }

    public BigDecimal getRiderLat() {
        return this.riderLat;
    }

    public void setRiderLat(BigDecimal riderLat) {
        this.riderLat = riderLat;
    }

    public BigDecimal getRiderLng() {
        return this.riderLng;
    }

    public void setRiderLng(BigDecimal riderLng) {
        this.riderLng = riderLng;
    }

    public LocalDateTime getRiderUpdatedAt() {
        return this.riderUpdatedAt;
    }

    public void setRiderUpdatedAt(LocalDateTime riderUpdatedAt) {
        this.riderUpdatedAt = riderUpdatedAt;
    }

    public LocalDateTime getDispatchedAt() {
        return this.dispatchedAt;
    }

    public void setDispatchedAt(LocalDateTime dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Integer getSaleId() {
        return this.saleId;
    }

    public void setSaleId(Integer saleId) {
        this.saleId = saleId;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<OnlineOrderItem> getItems() {
        return this.items;
    }

    public void setItems(List<OnlineOrderItem> items) {
        this.items = items;
    }
}
