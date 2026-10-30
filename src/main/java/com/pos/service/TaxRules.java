package com.pos.service;

import java.math.BigDecimal;
import java.util.logging.Logger;

/** Reads the tax settings. Kept free of JavaFX so the phone app server can price orders too. */
public final class TaxRules {
    private static final Logger LOG = Logger.getLogger(TaxRules.class.getName());
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private TaxRules() {
    }

    public static BigDecimal parseRate(String value) {
        if (value != null && !value.isBlank()) {
            try {
                BigDecimal rate = new BigDecimal(value.trim().replace("%", ""));
                if (rate.signum() < 0) {
                    return BigDecimal.ZERO;
                } else {
                    return rate.compareTo(BigDecimal.ONE) > 0 ? rate.divide(HUNDRED) : rate;
                }
            } catch (NumberFormatException var2) {
                LOG.warning("Invalid tax_rate setting '" + value + "', using 0");
                return BigDecimal.ZERO;
            }
        } else {
            return BigDecimal.ZERO;
        }
    }

    public static boolean parseFlag(String value) {
        if (value == null) {
            return false;
        } else {
            String v = value.trim().toLowerCase();
            return v.equals("true") || v.equals("1") || v.equals("yes");
        }
    }
}
