package com.pos.service;

import com.pos.model.CartItem;
import com.pos.model.Product;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class CartService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final CartService INSTANCE = new CartService();
    private final ObservableList<CartItem> items = FXCollections.observableArrayList(item -> new Observable[]{item.quantityProperty()});
    private final ObjectProperty<BigDecimal> taxRate = new SimpleObjectProperty(BigDecimal.ZERO);
    private final BooleanProperty pricesIncludeTax = new SimpleBooleanProperty(false);
    private final ObjectBinding<BigDecimal> subtotal;
    private final ObjectBinding<BigDecimal> tax = Bindings.createObjectBinding(
        this::computeTax, new Observable[]{this.items, this.taxRate, this.pricesIncludeTax}
    );
    private final ObjectBinding<BigDecimal> total;
    private final IntegerBinding itemCount;

    private CartService() {
        this.subtotal = Bindings.createObjectBinding(
            () -> this.pricesIncludeTax.get() ? this.lineSum().subtract((BigDecimal)this.tax.get()) : this.lineSum(), new Observable[]{this.items, this.tax}
        );
        this.total = Bindings.createObjectBinding(
            () -> this.pricesIncludeTax.get() ? this.lineSum() : this.lineSum().add((BigDecimal)this.tax.get()), new Observable[]{this.items, this.tax}
        );
        this.itemCount = Bindings.createIntegerBinding(() -> this.items.stream().mapToInt(CartItem::getQuantity).sum(), new Observable[]{this.items});
    }

    public static CartService getInstance() {
        return INSTANCE;
    }

    public ObservableList<CartItem> getItems() {
        return this.items;
    }

    public Optional<CartItem> find(Product product) {
        return this.items.stream().filter(i -> i.getProduct().getId() == product.getId()).findFirst();
    }

    public boolean add(Product product, int quantity) {
        Optional<CartItem> existing = this.find(product);
        int newQuantity = existing.map(CartItem::getQuantity).orElse(0) + quantity;
        if (quantity >= 1 && newQuantity <= product.getStock()) {
            if (existing.isPresent()) {
                existing.get().setQuantity(newQuantity);
            } else {
                this.items.add(new CartItem(product, newQuantity));
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean setQuantity(CartItem item, int quantity) {
        if (quantity >= 1 && quantity <= item.getProduct().getStock()) {
            item.setQuantity(quantity);
            return true;
        } else {
            return false;
        }
    }

    public boolean increment(CartItem item) {
        return this.setQuantity(item, item.getQuantity() + 1);
    }

    public boolean decrement(CartItem item) {
        return this.setQuantity(item, item.getQuantity() - 1);
    }

    public void remove(CartItem item) {
        this.items.remove(item);
    }

    public void clear() {
        this.items.clear();
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public void updateStock(List<Product> freshProducts) {
        Map<Integer, Integer> stockById = new HashMap<>();

        for (Product product : freshProducts) {
            stockById.put(product.getId(), product.getStock());
        }

        for (CartItem item : this.items) {
            Product product = item.getProduct();
            product.setStock(stockById.getOrDefault(product.getId(), 0));
        }
    }

    public void applyTaxSettings(String taxRateSetting, String pricesIncludeTaxSetting) {
        this.taxRate.set(parseRate(taxRateSetting));
        this.pricesIncludeTax.set(parseFlag(pricesIncludeTaxSetting));
    }

    static BigDecimal parseRate(String value) {
        return TaxRules.parseRate(value);
    }

    public static boolean parseFlag(String value) {
        return TaxRules.parseFlag(value);
    }

    public String taxPercentText() {
        return ((BigDecimal)this.taxRate.get()).multiply(HUNDRED).stripTrailingZeros().toPlainString();
    }

    public ObjectProperty<BigDecimal> taxRateProperty() {
        return this.taxRate;
    }

    public BooleanProperty pricesIncludeTaxProperty() {
        return this.pricesIncludeTax;
    }

    private BigDecimal lineSum() {
        return Money.of(this.items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal computeTax() {
        BigDecimal sum = this.lineSum();
        BigDecimal rate = (BigDecimal)this.taxRate.get();
        if (this.pricesIncludeTax.get()) {
            BigDecimal net = sum.divide(BigDecimal.ONE.add(rate), 2, Money.ROUNDING);
            return Money.of(sum.subtract(net));
        } else {
            return Money.of(sum.multiply(rate));
        }
    }

    public ObservableValue<BigDecimal> subtotalProperty() {
        return this.subtotal;
    }

    public ObservableValue<BigDecimal> taxProperty() {
        return this.tax;
    }

    public ObservableValue<BigDecimal> totalProperty() {
        return this.total;
    }

    public ObservableValue<Number> itemCountProperty() {
        return this.itemCount;
    }

    public BigDecimal getSubtotal() {
        return (BigDecimal)this.subtotal.get();
    }

    public BigDecimal getTax() {
        return (BigDecimal)this.tax.get();
    }

    public BigDecimal getTotal() {
        return (BigDecimal)this.total.get();
    }

    public int getItemCount() {
        return this.itemCount.get();
    }
}
