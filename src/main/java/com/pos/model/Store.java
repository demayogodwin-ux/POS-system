package com.pos.model;

import java.math.BigDecimal;

public record Store(int id, String name, String address, BigDecimal latitude, BigDecimal longitude, String phone, String hours) {
}
