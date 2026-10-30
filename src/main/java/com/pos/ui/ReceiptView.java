package com.pos.ui;

import com.pos.model.PaymentMethod;
import com.pos.model.Sale;
import com.pos.model.SaleItem;
import com.pos.service.Money;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

public class ReceiptView extends VBox {
    private static final double WIDTH = 340.0;
    private static final String TEXT_STYLE = "-fx-text-fill: #1C1C1C;";
    private static final String BOLD_STYLE = "-fx-text-fill: #1C1C1C; -fx-font-weight: 700;";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    public ReceiptView(Sale sale, ReceiptView.StoreInfo store) {
        this.setStyle("-fx-background-color: #F5F3EE; -fx-padding: 20 18 20 18; -fx-spacing: 4; -fx-font-family: 'JetBrains Mono'; -fx-font-size: 13px;");
        this.setMinWidth(340.0);
        this.setPrefWidth(340.0);
        this.setMaxWidth(340.0);
        this.getStyleClass().add("receipt-paper");
        this.getChildren().add(centered(store.name(), true));
        if (!isBlank(store.address())) {
            this.getChildren().add(centered(store.address(), false));
        }

        this.getChildren().add(separator());
        this.getChildren().add(row("Receipt", sale.getReceiptNo(), false));
        if (sale.getSaleTime() != null) {
            this.getChildren().add(row("Date", sale.getSaleTime().format(DATE_TIME), false));
        }

        this.getChildren().add(row("Cashier", sale.getCashierName(), false));
        this.getChildren().add(separator());

        for (SaleItem item : sale.getItems()) {
            this.getChildren().add(itemRow(item));
        }

        this.getChildren().add(separator());
        this.getChildren().add(row("Subtotal", Money.currency(sale.getSubtotal()), false));
        this.getChildren().add(row("Tax", Money.currency(sale.getTax()), false));
        this.getChildren().add(row("TOTAL", Money.currency(sale.getTotal()), true));
        this.getChildren().add(row(sale.getPaymentMethod().label(), Money.currency(sale.getAmountPaid()), false));
        if (sale.getPaymentMethod() == PaymentMethod.CASH) {
            this.getChildren().add(row("Change", Money.currency(sale.getChangeDue()), false));
        }

        if (!isBlank(store.footer())) {
            this.getChildren().add(separator());
            this.getChildren().add(centered(store.footer(), false));
        }
    }

    private static Label text(String value, boolean bold) {
        Label label = new Label(value == null ? "" : value);
        label.setStyle(bold ? "-fx-text-fill: #1C1C1C; -fx-font-weight: 700;" : "-fx-text-fill: #1C1C1C;");
        return label;
    }

    private static Label centered(String value, boolean bold) {
        Label label = text(value, bold);
        label.setWrapText(true);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static HBox row(String name, String value, boolean bold) {
        Label left = text(name, bold);
        Label right = text(value, bold);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(8.0, new Node[]{left, spacer, right});
    }

    private static HBox itemRow(SaleItem item) {
        Label name = text(item.getProductName(), false);
        name.setWrapText(true);
        name.setMinWidth(0.0);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Label quantity = text("x" + item.getQuantity(), false);
        quantity.setMinWidth(40.0);
        quantity.setAlignment(Pos.CENTER_RIGHT);
        Label total = text(Money.currency(item.getLineTotal()), false);
        total.setMinWidth(Double.NEGATIVE_INFINITY);
        total.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(8.0, new Node[]{name, quantity, total});
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static Region separator() {
        Region line = new Region();
        line.setStyle("-fx-border-color: #1C1C1C; -fx-border-width: 1 0 0 0; -fx-border-style: dashed;");
        line.setMinHeight(1.0);
        line.setPrefHeight(1.0);
        VBox.setMargin(line, new Insets(6.0, 0.0, 6.0, 0.0));
        return line;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String changeMessage(Sale sale) {
        if (sale.getPaymentMethod() != PaymentMethod.CASH) {
            return "Paid by " + sale.getPaymentMethod().label();
        } else {
            BigDecimal change = sale.getChangeDue();
            return change.signum() == 0 ? "No change due" : "Give the customer " + Money.currency(change) + " change";
        }
    }

    public record StoreInfo(String name, String address, String footer) {
        public static ReceiptView.StoreInfo fromSettings(Map<String, String> settings) {
            String name = settings.get("store_name");
            return new ReceiptView.StoreInfo(name != null && !name.isBlank() ? name : "My Store", settings.get("store_address"), settings.get("receipt_footer"));
        }
    }
}
