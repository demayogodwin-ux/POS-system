package com.pos.model;

import com.pos.service.Money;
import java.math.BigDecimal;

public class Product {
    private int id;
    private String code;
    private String name;
    private Integer categoryId;
    private String categoryName;
    private BigDecimal price = Money.ZERO;
    private int stock;
    private int lowStockLevel;
    private boolean active = true;
    private String location;
    // When the photo was last changed (seconds), or 0 when the product has no photo; also busts image caches.
    private long imageVersion;

    public Product() {
    }

    public Product(int id, String code, String name, Integer categoryId, String categoryName, BigDecimal price, int stock, int lowStockLevel, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.setPrice(price);
        this.stock = stock;
        this.lowStockLevel = lowStockLevel;
        this.active = active;
    }

    public boolean isLowStock() {
        return this.stock <= this.lowStockLevel;
    }

    public boolean isInStock() {
        return this.stock > 0;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCategoryId() {
        return this.categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return this.categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public BigDecimal getPrice() {
        return this.price;
    }

    public void setPrice(BigDecimal price) {
        this.price = Money.of(price);
    }

    public int getStock() {
        return this.stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int getLowStockLevel() {
        return this.lowStockLevel;
    }

    public void setLowStockLevel(int lowStockLevel) {
        this.lowStockLevel = lowStockLevel;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean hasImage() {
        return this.imageVersion > 0;
    }

    public long getImageVersion() {
        return this.imageVersion;
    }

    public void setImageVersion(long imageVersion) {
        this.imageVersion = imageVersion;
    }

    public String getLocation() {
        return this.location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return this.code + " " + this.name;
    }
}
