package com.pos.service;

import com.pos.model.Category;
import com.pos.model.Product;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;

public final class ProductService {
    public static final String DUPLICATE_CODE_MESSAGE = "This code is already used";
    private static final int MAX_CODE_LENGTH = 50;
    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_LOCATION_LENGTH = 100;

    private ProductService() {
    }

    public static Product validate(
        int id, String code, String name, Category category, String price, String stock, String lowStockLevel, String location, Collection<Product> existing
    ) throws ProductService.ValidationException {
        String cleanCode = code == null ? "" : code.trim();
        if (cleanCode.isEmpty()) {
            throw new ProductService.ValidationException(ProductService.Field.CODE, "Product code is required");
        } else if (cleanCode.length() > 50) {
            throw new ProductService.ValidationException(ProductService.Field.CODE, "Product code can be at most 50 characters");
        } else {
            String codeKey = cleanCode.toLowerCase(Locale.ROOT);
            boolean duplicate = existing.stream().anyMatch(p -> p.getId() != id && p.getCode().toLowerCase(Locale.ROOT).equals(codeKey));
            if (duplicate) {
                throw new ProductService.ValidationException(ProductService.Field.CODE, "This code is already used");
            } else {
                String cleanName = name == null ? "" : name.trim();
                if (cleanName.isEmpty()) {
                    throw new ProductService.ValidationException(ProductService.Field.NAME, "Name is required");
                } else if (cleanName.length() > 150) {
                    throw new ProductService.ValidationException(ProductService.Field.NAME, "Name can be at most 150 characters");
                } else {
                    BigDecimal parsedPrice = parsePrice(price);
                    int parsedStock = parseWholeNumber(stock, ProductService.Field.STOCK, "Stock");
                    int parsedLevel = parseWholeNumber(lowStockLevel, ProductService.Field.LOW_STOCK_LEVEL, "Low-stock level");
                    String cleanLocation = location == null ? "" : location.trim();
                    if (cleanLocation.length() > MAX_LOCATION_LENGTH) {
                        throw new ProductService.ValidationException(ProductService.Field.LOCATION, "Location can be at most 100 characters");
                    }

                    Product product = new Product();
                    product.setId(id);
                    product.setCode(cleanCode);
                    product.setName(cleanName);
                    product.setCategoryId(category == null ? null : category.getId());
                    product.setCategoryName(category == null ? null : category.getName());
                    product.setPrice(parsedPrice);
                    product.setStock(parsedStock);
                    product.setLowStockLevel(parsedLevel);
                    product.setLocation(cleanLocation.isEmpty() ? null : cleanLocation);
                    product.setActive(true);
                    return product;
                }
            }
        }
    }

    private static BigDecimal parsePrice(String text) throws ProductService.ValidationException {
        String value = text == null ? "" : text.trim();
        if (!value.matches("\\d{1,8}(\\.\\d{1,2})?")) {
            throw new ProductService.ValidationException(ProductService.Field.PRICE, "Price must be 0 or more, with at most 2 decimals");
        } else {
            return Money.of(new BigDecimal(value));
        }
    }

    private static int parseWholeNumber(String text, ProductService.Field field, String label) throws ProductService.ValidationException {
        String value = text == null ? "" : text.trim();
        if (!value.matches("\\d{1,9}")) {
            throw new ProductService.ValidationException(field, label + " must be a whole number, 0 or more");
        } else {
            return Integer.parseInt(value);
        }
    }

    public static Integer tryParseWholeNumber(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("\\d{1,9}") ? Integer.valueOf(value) : null;
    }

    public static enum Field {
        CODE,
        NAME,
        CATEGORY,
        PRICE,
        STOCK,
        LOW_STOCK_LEVEL,
        LOCATION;
    }

    public static class ValidationException extends Exception {
        private final ProductService.Field field;

        public ValidationException(ProductService.Field field, String message) {
            super(message);
            this.field = field;
        }

        public ProductService.Field getField() {
            return this.field;
        }
    }
}
