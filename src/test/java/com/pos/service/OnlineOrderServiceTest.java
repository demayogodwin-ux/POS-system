package com.pos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pos.model.Customer;
import com.pos.model.OnlineOrder;
import com.pos.model.OnlineOrderItem;
import com.pos.model.OrderStatus;
import com.pos.model.OrderType;
import com.pos.model.PaymentMethod;
import com.pos.model.Product;
import com.pos.model.Role;
import com.pos.model.Sale;
import com.pos.model.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OnlineOrderServiceTest {
    private static final List<OnlineOrderItem> LINES = List.of(
        OnlineOrderService.line(new Product(1, "A", "Coffee", null, null, new BigDecimal("50.00"), 10, 5, true), 2),
        OnlineOrderService.line(new Product(2, "B", "Soap", null, null, new BigDecimal("25.00"), 10, 5, true), 1)
    );

    @Test
    void addsTaxLikeTheSaleScreen() {
        OnlineOrderService.Totals totals = OnlineOrderService.price(LINES, "0.12", "false");
        assertEquals(new BigDecimal("125.00"), totals.subtotal());
        assertEquals(new BigDecimal("15.00"), totals.tax());
        assertEquals(new BigDecimal("140.00"), totals.total());
    }

    @Test
    void takesTaxOutOfTaxInclusivePrices() {
        OnlineOrderService.Totals totals = OnlineOrderService.price(LINES, "0.12", "true");
        assertEquals(new BigDecimal("125.00"), totals.total());
        assertEquals(new BigDecimal("13.39"), totals.tax());
        assertEquals(new BigDecimal("111.61"), totals.subtotal());
    }

    @Test
    void completedOrderBecomesAPaidSaleWithTheSameLines() {
        OnlineOrder order = new OnlineOrder();
        order.setItems(LINES);
        order.setSubtotal(new BigDecimal("125.00"));
        order.setTax(new BigDecimal("15.00"));
        order.setTotal(new BigDecimal("140.00"));
        order.setPaymentMethod(PaymentMethod.EWALLET);
        Sale sale = OnlineOrderService.toSale(order, new User(3, "ana", "Ana Cruz", Role.CASHIER, true));
        assertEquals(3, sale.getUserId());
        assertEquals(PaymentMethod.EWALLET, sale.getPaymentMethod());
        assertEquals(new BigDecimal("140.00"), sale.getAmountPaid());
        assertEquals(new BigDecimal("0.00"), sale.getChangeDue());
        assertEquals(3, sale.itemCount());
        assertTrue(sale.isPaid());
    }

    @Test
    void statusFlowDependsOnOrderType() {
        assertEquals(OrderStatus.PREPARING, OrderStatus.PENDING.next(OrderType.DELIVERY));
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.PREPARING.next(OrderType.DELIVERY));
        assertEquals(OrderStatus.READY, OrderStatus.PREPARING.next(OrderType.IN_STORE));
        assertEquals(OrderStatus.COMPLETED, OrderStatus.READY.next(OrderType.IN_STORE));
        assertNull(OrderStatus.COMPLETED.next(OrderType.DELIVERY));
        assertNull(OrderStatus.CANCELLED.next(OrderType.IN_STORE));
        assertFalse(OrderStatus.CANCELLED.isOpen());
    }

    @Test
    void riderCodesAreEightUnambiguousCharacters() {
        String code = OnlineOrderService.newRiderCode();
        assertTrue(code.matches("[A-HJ-NP-Z2-9]{8}"), code);
    }

    @Test
    void cleansUpSignUpDetails() throws ValidationException {
        Customer customer = CustomerService.validateProfile("  Juan Dela Cruz ", "0917 123-4567", " Juan@Example.com ", "Jaro, Iloilo");
        assertEquals("Juan Dela Cruz", customer.fullName());
        assertEquals("09171234567", customer.phone());
        assertEquals("juan@example.com", customer.email());
        assertNull(CustomerService.validateProfile("Ana", "09171234567", "", "").email());
    }

    @Test
    void rejectsBadSignUpDetails() {
        assertEquals("fullName", assertThrows(ValidationException.class, () -> CustomerService.validateProfile("", "09171234567", "", "")).getField());
        assertEquals("phone", assertThrows(ValidationException.class, () -> CustomerService.validateProfile("Ana", "12ab", "", "")).getField());
        assertEquals("email", assertThrows(ValidationException.class, () -> CustomerService.validateProfile("Ana", "09171234567", "not-an-email", "")).getField());
        assertEquals("password", assertThrows(ValidationException.class, () -> CustomerService.validatePassword("123")).getField());
    }

    @Test
    void passwordHashesAreSaltedAndVerifiable() {
        String salt = CustomerService.newSalt();
        String hash = CustomerService.hashPassword("secret1", salt);
        assertEquals(64, hash.length());
        assertTrue(CustomerService.passwordMatches("secret1", salt, hash));
        assertFalse(CustomerService.passwordMatches("secret2", salt, hash));
        assertFalse(hash.equals(CustomerService.hashPassword("secret1", CustomerService.newSalt())));
    }
}
