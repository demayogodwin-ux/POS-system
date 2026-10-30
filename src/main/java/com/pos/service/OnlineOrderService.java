package com.pos.service;

import com.pos.model.OnlineOrder;
import com.pos.model.OnlineOrderItem;
import com.pos.model.Product;
import com.pos.model.Sale;
import com.pos.model.SaleItem;
import com.pos.model.User;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/** Pricing and hand-off rules for orders placed from the customer phone app. */
public final class OnlineOrderService {
    public static final int MAX_QUANTITY = 99;
    public static final int MAX_LINES = 50;
    private static final String RIDER_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private OnlineOrderService() {
    }

    public static OnlineOrderItem line(Product product, int quantity) {
        return new OnlineOrderItem(
            product.getId(), product.getName(), quantity, product.getPrice(), Money.of(product.getPrice().multiply(BigDecimal.valueOf((long)quantity)))
        );
    }

    /** Same tax rules as the Sale screen (CartService), so a phone order costs what the counter would charge. */
    public static Totals price(List<OnlineOrderItem> lines, String taxRateSetting, String pricesIncludeTaxSetting) {
        BigDecimal sum = Money.of(lines.stream().map(OnlineOrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal rate = TaxRules.parseRate(taxRateSetting);
        if (TaxRules.parseFlag(pricesIncludeTaxSetting)) {
            BigDecimal net = sum.divide(BigDecimal.ONE.add(rate), 2, Money.ROUNDING);
            BigDecimal tax = Money.of(sum.subtract(net));
            return new Totals(Money.of(sum.subtract(tax)), tax, sum);
        } else {
            BigDecimal tax = Money.of(sum.multiply(rate));
            return new Totals(sum, tax, Money.of(sum.add(tax)));
        }
    }

    /** The sale that is recorded (and takes the stock) when staff completes the order. Paid in full on hand-over. */
    public static Sale toSale(OnlineOrder order, User staff) {
        if (staff == null) {
            throw new IllegalStateException("No user is signed in");
        } else {
            Sale sale = new Sale();
            sale.setUserId(staff.getId());
            sale.setCashierName(staff.getFullName());
            sale.setSaleTime(LocalDateTime.now());
            sale.setSubtotal(order.getSubtotal());
            sale.setTax(order.getTax());
            sale.setTotal(order.getTotal());
            sale.setPaymentMethod(order.getPaymentMethod());
            sale.setAmountPaid(order.getTotal());
            sale.setChangeDue(Money.ZERO);
            sale.setStatus("PAID");

            for (OnlineOrderItem item : order.getItems()) {
                sale.getItems().add(new SaleItem(0, 0, item.productId(), item.productName(), item.quantity(), item.unitPrice(), item.lineTotal()));
            }

            return sale;
        }
    }

    public static String newRiderCode() {
        StringBuilder code = new StringBuilder(8);

        for (int i = 0; i < 8; i++) {
            code.append(RIDER_CODE_CHARS.charAt(RANDOM.nextInt(RIDER_CODE_CHARS.length())));
        }

        return code.toString();
    }

    public record Totals(BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
    }
}
