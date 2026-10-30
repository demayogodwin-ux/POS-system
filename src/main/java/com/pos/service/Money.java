package com.pos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class Money {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, ROUNDING);
    }

    public static String format(BigDecimal value) {
        return String.format(Locale.US, "%,.2f", of(value));
    }

    public static String currency(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance();
        format.setRoundingMode(ROUNDING);
        return format.format(of(value));
    }

    public static BigDecimal nextStep(BigDecimal value, BigDecimal step) {
        BigDecimal steps = of(value).divide(step, 0, RoundingMode.FLOOR).add(BigDecimal.ONE);
        return of(steps.multiply(step));
    }
}
