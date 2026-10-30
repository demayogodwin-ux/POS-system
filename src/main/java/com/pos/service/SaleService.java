package com.pos.service;

import com.pos.model.CartItem;
import com.pos.model.PaymentMethod;
import com.pos.model.Product;
import com.pos.model.Sale;
import com.pos.model.SaleItem;
import com.pos.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SaleService {
    private SaleService() {
    }

    public static String receiptNumber(int sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("Receipt sequence must be 1 or more, was " + sequence);
        } else {
            return String.format("S%05d", sequence);
        }
    }

    public static Sale fromCart(CartService cart, User cashier, PaymentMethod method, BigDecimal cashReceived) {
        if (cashier == null) {
            throw new IllegalStateException("No user is signed in");
        } else {
            BigDecimal total = cart.getTotal();
            BigDecimal paid = method == PaymentMethod.CASH ? Money.of(cashReceived) : total;
            Sale sale = new Sale();
            sale.setUserId(cashier.getId());
            sale.setCashierName(cashier.getFullName());
            sale.setSaleTime(LocalDateTime.now());
            sale.setSubtotal(cart.getSubtotal());
            sale.setTax(cart.getTax());
            sale.setTotal(total);
            sale.setPaymentMethod(method);
            sale.setAmountPaid(paid);
            sale.setChangeDue(paid.subtract(total));
            sale.setStatus("PAID");

            for (CartItem item : cart.getItems()) {
                Product product = item.getProduct();
                sale.getItems().add(new SaleItem(0, 0, product.getId(), product.getName(), item.getQuantity(), product.getPrice(), item.lineTotal()));
            }

            return sale;
        }
    }
}
