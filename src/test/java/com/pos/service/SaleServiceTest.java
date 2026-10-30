package com.pos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pos.model.PaymentMethod;
import com.pos.model.Product;
import com.pos.model.Role;
import com.pos.model.Sale;
import com.pos.model.SaleItem;
import com.pos.model.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SaleServiceTest {
    private final CartService cart = CartService.getInstance();
    private final User cashier = new User(7, "cashier", "Ana Cruz", Role.CASHIER, true);

    @BeforeEach
    void fillCart() {
        cart.clear();
        cart.applyTaxSettings("0.12", "false");
        // 2 x 50.00 + 1 x 25.00 = 125.00, tax 15.00, total 140.00
        cart.add(new Product(1, "4800016644290", "Lucky Me Pancit Canton", null, null, new BigDecimal("50.00"), 10, 5, true), 2);
        cart.add(new Product(2, "4800047820014", "Zonrox", null, null, new BigDecimal("25.00"), 3, 5, true), 1);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void copiesTotalsCashierAndLines() {
        Sale sale = SaleService.fromCart(cart, cashier, PaymentMethod.CASH, money("200"));
        assertEquals(7, sale.getUserId());
        assertEquals("Ana Cruz", sale.getCashierName());
        assertNotNull(sale.getSaleTime());
        assertEquals(Sale.STATUS_PAID, sale.getStatus());
        assertEquals(money("125.00"), sale.getSubtotal());
        assertEquals(money("15.00"), sale.getTax());
        assertEquals(money("140.00"), sale.getTotal());
        assertEquals(2, sale.getItems().size());
        assertEquals(3, sale.itemCount());

        SaleItem first = sale.getItems().get(0);
        assertEquals(1, first.getProductId());
        assertEquals("Lucky Me Pancit Canton", first.getProductName());
        assertEquals(2, first.getQuantity());
        assertEquals(money("50.00"), first.getUnitPrice());
        assertEquals(money("100.00"), first.getLineTotal());
    }

    @Test
    void cashChangeIsCashMinusTotal() {
        Sale sale = SaleService.fromCart(cart, cashier, PaymentMethod.CASH, money("500"));
        assertEquals(money("500.00"), sale.getAmountPaid());
        assertEquals(money("360.00"), sale.getChangeDue());
    }

    @Test
    void exactCashGivesNoChange() {
        Sale sale = SaleService.fromCart(cart, cashier, PaymentMethod.CASH, money("140"));
        assertEquals(money("0.00"), sale.getChangeDue());
    }

    @Test
    void cardAndEwalletPayExactlyTheTotal() {
        for (PaymentMethod method : new PaymentMethod[]{PaymentMethod.CARD, PaymentMethod.EWALLET}) {
            Sale sale = SaleService.fromCart(cart, cashier, method, null);
            assertEquals(method, sale.getPaymentMethod());
            assertEquals(money("140.00"), sale.getAmountPaid());
            assertEquals(money("0.00"), sale.getChangeDue());
        }
    }

    @Test
    void requiresASignedInCashier() {
        assertThrows(IllegalStateException.class,
            () -> SaleService.fromCart(cart, null, PaymentMethod.CASH, money("200")));
    }

    @ParameterizedTest
    @CsvSource({
        "1, S00001",
        "42, S00042",
        "99999, S99999",
        "123456, S123456"
    })
    void receiptNumberFormat(int sequence, String expected) {
        assertEquals(expected, SaleService.receiptNumber(sequence));
    }

    @Test
    void receiptNumberRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> SaleService.receiptNumber(0));
    }

    @ParameterizedTest
    @CsvSource({
        "140.00, 100, 200.00",
        "140.00, 500, 500.00",
        "140.00, 1000, 1000.00",
        "500.00, 500, 1000.00",
        "0.01, 100, 100.00"
    })
    void quickCashAmounts(String total, String step, String expected) {
        assertEquals(money(expected), Money.nextStep(money(total), money(step)));
    }
}
