package com.pos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pos.model.CartItem;
import com.pos.model.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CartServiceTest {
    private final CartService cart = CartService.getInstance();
    private int nextId = 1;

    @BeforeEach
    void resetCart() {
        cart.clear();
        cart.applyTaxSettings("0.12", "false");
    }

    private Product product(String price, int stock) {
        int id = nextId++;
        return new Product(id, "P" + id, "Product " + id, null, null, new BigDecimal(price), stock, 5, true);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void emptyCart() {
        assertTrue(cart.isEmpty());
        assertEquals(0, cart.getItemCount());
        assertEquals(money("0.00"), cart.getSubtotal());
        assertEquals(money("0.00"), cart.getTax());
        assertEquals(money("0.00"), cart.getTotal());
    }

    @ParameterizedTest
    @CsvSource({
        "0.12, 0.12",
        "12, 0.12",
        "12%, 0.12",
        "' 10 ', 0.1",
        "0, 0",
        "-5, 0",
        "abc, 0",
        "'', 0"
    })
    void parsesStoredTaxRate(String stored, String expected) {
        assertEquals(0, money(expected).compareTo(CartService.parseRate(stored)), stored);
    }

    @Nested
    class TaxExclusive {
        @Test
        void taxIsSubtotalTimesRate() {
            cart.add(product("100.00", 10), 2);
            assertEquals(money("200.00"), cart.getSubtotal());
            assertEquals(money("24.00"), cart.getTax());
            assertEquals(money("224.00"), cart.getTotal());
        }

        @Test
        void totalsFollowQuantityChanges() {
            cart.add(product("50.00", 10), 1);
            CartItem item = cart.getItems().get(0);
            assertTrue(cart.increment(item));
            assertEquals(2, cart.getItemCount());
            assertEquals(money("100.00"), cart.getSubtotal());
            assertEquals(money("112.00"), cart.getTotal());

            assertTrue(cart.decrement(item));
            assertEquals(money("56.00"), cart.getTotal());
        }
    }

    @Nested
    class TaxInclusive {
        @BeforeEach
        void includeTax() {
            cart.applyTaxSettings("12", "true");
        }

        @Test
        void totalIsTheShelfPriceAndTaxIsExtractedFromIt() {
            cart.add(product("112.00", 10), 1);
            assertEquals(money("112.00"), cart.getTotal());
            assertEquals(money("12.00"), cart.getTax());
            assertEquals(money("100.00"), cart.getSubtotal());
        }

        @Test
        void subtotalPlusTaxAlwaysEqualsTotal() {
            for (String price : new String[]{"0.01", "0.99", "1.00", "19.95", "33.33", "99.99", "1234.56"}) {
                cart.clear();
                cart.add(product(price, 100), 3);
                assertEquals(cart.getTotal(), cart.getSubtotal().add(cart.getTax()), price);
                assertEquals(Money.of(money(price).multiply(BigDecimal.valueOf(3))), cart.getTotal(), price);
            }
        }
    }

    @Nested
    class StockLimits {
        @Test
        void cannotAddMoreThanStock() {
            Product zonrox = product("45.00", 3);
            assertTrue(cart.add(zonrox, 2));
            assertTrue(cart.add(zonrox, 1));
            assertFalse(cart.add(zonrox, 1));
            CartItem item = cart.getItems().get(0);
            assertEquals(3, item.getQuantity());
            assertFalse(cart.increment(item));
            assertFalse(cart.setQuantity(item, 10));
            assertEquals(3, item.getQuantity());

            assertFalse(cart.add(product("10.00", 0), 1));
            assertEquals(1, cart.getItems().size());
        }

        @Test
        void quantityNeverBelowOne() {
            cart.add(product("10.00", 5), 1);
            CartItem item = cart.getItems().get(0);
            assertFalse(cart.decrement(item));
            assertFalse(cart.setQuantity(item, 0));
            assertFalse(cart.add(item.getProduct(), 0));
            assertEquals(1, item.getQuantity());
        }
    }

    @Nested
    class Rounding {
        @ParameterizedTest
        @CsvSource({
            "1.005, 1.01",
            "1.004, 1.00",
            "2.5, 2.50",
            "-1.005, -1.01"
        })
        void moneyOfRoundsHalfUp(String value, String expected) {
            assertEquals(money(expected), Money.of(money(value)));
        }

        @Test
        void halfCentRoundsUpOnExclusiveTax() {
            // 0.125 tax rounds up to 0.13
            cart.add(product("1.25", 10), 1);
            cart.applyTaxSettings("0.1", "false");
            assertEquals(money("0.13"), cart.getTax());
            assertEquals(money("1.38"), cart.getTotal());
        }

        @Test
        void inclusiveTaxOnOnePeso() {
            cart.applyTaxSettings("12", "true");
            cart.add(product("1.00", 10), 1);
            // 1.00 / 1.12 = 0.89 net, 0.11 tax
            assertEquals(money("0.11"), cart.getTax());
            assertEquals(money("0.89"), cart.getSubtotal());
            assertEquals(money("1.00"), cart.getTotal());
        }

        @Test
        void lineTotalsKeepTwoDecimals() {
            cart.add(product("0.333", 10), 3);
            CartItem item = cart.getItems().get(0);
            assertEquals(money("0.33"), item.getProduct().getPrice());
            assertEquals(money("0.99"), item.lineTotal());
            assertEquals(2, cart.getSubtotal().scale());
            assertEquals(2, cart.getTax().scale());
            assertEquals(2, cart.getTotal().scale());
        }
    }
}
