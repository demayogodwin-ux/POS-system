package com.pos.ui;

import com.pos.model.CartItem;
import com.pos.service.Money;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

class CartItemCell extends ListCell<CartItem> {
    private static final Color TEXT = Color.web("#ECEEF1");
    private final Button minus = new Button(null, Icons.graphic(Icons.Icon.MINUS, TEXT));
    private final Label quantity = new Label();
    private final Button plus = new Button(null, Icons.graphic(Icons.Icon.PLUS, TEXT));
    private final Label name = new Label();
    private final Label lineTotal = new Label();
    private final HBox stepper = new HBox(4.0, new Node[]{this.minus, this.quantity, this.plus});
    private final HBox row = new HBox(10.0, new Node[]{this.stepper, this.name, this.lineTotal});
    private final ContextMenu menu;

    /** onDecrement takes one off and removes the line when it reaches zero; the buttons let a cashier fix a mistake without clearing the cart. */
    CartItemCell(Consumer<CartItem> onIncrement, Consumer<CartItem> onDecrement, Consumer<CartItem> onChangeQuantity, Consumer<CartItem> onRemove) {
        this.quantity.getStyleClass().addAll(new String[]{"cart-qty", "mono"});
        this.name.getStyleClass().add("cart-name");
        this.lineTotal.getStyleClass().addAll(new String[]{"cart-line-total", "mono"});
        this.name.setMinWidth(0.0);
        // Wrap instead of cutting off, so "Cheesecake (slice)" and "Cheesecake (whole)" stay distinguishable.
        this.name.setWrapText(true);
        this.name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(this.name, Priority.ALWAYS);
        this.lineTotal.setMinWidth(Double.NEGATIVE_INFINITY);
        this.stepper.setAlignment(Pos.CENTER_LEFT);
        this.stepper.setMinWidth(Double.NEGATIVE_INFINITY);
        this.row.setAlignment(Pos.CENTER_LEFT);
        this.row.getStyleClass().add("cart-row");

        this.minus.getStyleClass().addAll("btn-icon", "btn-icon-sm", "cart-step");
        this.minus.setFocusTraversable(false);
        this.minus.setOnAction(e -> {
            if (this.getItem() != null) {
                onDecrement.accept((CartItem)this.getItem());
            }
        });
        this.plus.getStyleClass().addAll("btn-icon", "btn-icon-sm", "cart-step");
        this.plus.setFocusTraversable(false);
        this.plus.setTooltip(new Tooltip("Add one more"));
        this.plus.setOnAction(e -> {
            if (this.getItem() != null) {
                onIncrement.accept((CartItem)this.getItem());
            }
        });

        MenuItem change = new MenuItem("Change quantity...");
        change.setOnAction(e -> {
            if (this.getItem() != null) {
                onChangeQuantity.accept((CartItem)this.getItem());
            }
        });
        MenuItem remove = new MenuItem("Remove");
        remove.setOnAction(e -> {
            if (this.getItem() != null) {
                onRemove.accept((CartItem)this.getItem());
            }
        });
        this.menu = new ContextMenu(new MenuItem[]{change, remove});
        this.setPrefWidth(0.0);
        this.setText(null);
    }

    protected void updateItem(CartItem item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty && item != null) {
            this.quantity.setText(String.valueOf(item.getQuantity()));
            this.minus.setTooltip(new Tooltip(item.getQuantity() > 1 ? "Take one off" : "Remove from cart"));
            this.name.setText(item.getProduct().getName());
            this.lineTotal.setText(Money.format(item.lineTotal()));
            this.setGraphic(this.row);
            this.setContextMenu(this.menu);
        } else {
            this.setGraphic(null);
            this.setContextMenu(null);
        }
    }
}
