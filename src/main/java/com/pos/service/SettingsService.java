package com.pos.service;

import java.math.BigDecimal;

public final class SettingsService {
    public static final String STORE_NAME = "store_name";
    public static final String STORE_ADDRESS = "store_address";
    public static final String RECEIPT_FOOTER = "receipt_footer";
    public static final String TAX_RATE = "tax_rate";
    public static final String PRICES_INCLUDE_TAX = "prices_include_tax";
    public static final String PRINT_RECEIPT = "print_receipt";
    public static final String OPEN_CASH_DRAWER = "open_cash_drawer";
    public static final String BEEP_ON_SCAN = "beep_on_scan";
    public static final String GOOGLE_MAPS_API_KEY = "google_maps_api_key";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SettingsService() {
    }

    public static String taxPercentForDisplay(String stored) {
        return CartService.parseRate(stored).multiply(HUNDRED).stripTrailingZeros().toPlainString();
    }

    public static String taxRateToStore(String percentText) throws ValidationException {
        String value = percentText == null ? "" : percentText.trim();
        if (!value.matches("\\d{1,3}(\\.\\d{1,2})?")) {
            throw new ValidationException("tax_rate", "Tax rate must be a number from 0 to 100, with at most 2 decimals");
        } else {
            BigDecimal percent = new BigDecimal(value);
            if (percent.compareTo(HUNDRED) > 0) {
                throw new ValidationException("tax_rate", "Tax rate cannot be more than 100%");
            } else {
                BigDecimal fraction = percent.movePointLeft(2).stripTrailingZeros();
                return fraction.signum() == 0 ? "0" : fraction.toPlainString();
            }
        }
    }

    public static String requireStoreName(String name) throws ValidationException {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            throw new ValidationException("store_name", "Store name is required (it is printed on receipts)");
        } else if (value.length() > 255) {
            throw new ValidationException("store_name", "Store name can be at most 255 characters");
        } else {
            return value;
        }
    }

    public static String limitText(String key, String label, String text) throws ValidationException {
        String value = text == null ? "" : text.trim();
        if (value.length() > 255) {
            throw new ValidationException(key, label + " can be at most 255 characters");
        } else {
            return value;
        }
    }

    public static String mapsKeyToStore(String key) throws ValidationException {
        String value = key == null ? "" : key.trim();
        if (!value.matches("[A-Za-z0-9_-]{0,100}")) {
            throw new ValidationException("google_maps_api_key", "The Google Maps API key can only contain letters, digits, - and _");
        } else {
            return value;
        }
    }
}
