package com.pos.model;

import com.pos.service.Money;
import java.math.BigDecimal;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public class CartItem {
    private final Product product;
    private final IntegerProperty quantity = new SimpleIntegerProperty();

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity.set(quantity);
    }

    public Product getProduct() {
        return this.product;
    }

    public int getQuantity() {
        return this.quantity.get();
    }

    public void setQuantity(int value) {
        this.quantity.set(value);
    }

    public IntegerProperty quantityProperty() {
        return this.quantity;
    }

    public BigDecimal lineTotal() {
        return Money.of(this.product.getPrice().multiply(BigDecimal.valueOf((long)this.getQuantity())));
    }
}
