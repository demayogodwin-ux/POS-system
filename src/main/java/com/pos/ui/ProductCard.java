package com.pos.ui;

import com.pos.model.Product;
import com.pos.service.Money;
import java.util.function.Consumer;
import javafx.css.PseudoClass;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

/** A Sale screen tile: big photo, name, price and stock. Clicking it adds the product to the cart. */
class ProductCard extends VBox {
    static final double WIDTH = 164.0;
    private static final double PHOTO = WIDTH - 20.0;
    private static final PseudoClass OUT_OF_STOCK = PseudoClass.getPseudoClass("out-of-stock");

    ProductCard(Product product, Consumer<Product> onAdd) {
        super(6.0);
        ProductImages.Thumbnail photo = new ProductImages.Thumbnail(PHOTO);
        photo.show(product);
        Label name = new Label(product.getName());
        name.getStyleClass().add("product-card-name");
        name.setWrapText(true);
        // Two lines of name for every card, so prices line up across a row.
        // Room for two full lines: a label only wraps when the second line fits, otherwise it cuts the name off.
        name.setMinHeight(42.0);
        name.setPrefHeight(42.0);
        // A fixed width lets the label wrap; without it the label asks for one long line and gets cut off.
        name.setMinWidth(PHOTO);
        name.setPrefWidth(PHOTO);
        name.setMaxWidth(PHOTO);
        name.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        Label price = new Label(Money.format(product.getPrice()));
        price.getStyleClass().addAll("product-card-price", "mono");
        Label stock = new Label(!product.isInStock() ? "Out of stock" : product.isLowStock() ? "Only " + product.getStock() + " left" : product.getStock() + " in stock");
        stock.getStyleClass().add(product.isInStock() && !product.isLowStock() ? "product-card-stock" : "product-card-stock-warn");

        this.getChildren().addAll(photo, name, price, stock);
        this.getStyleClass().add("product-card");
        this.setMinWidth(WIDTH);
        this.setPrefWidth(WIDTH);
        this.setMaxWidth(WIDTH);
        this.pseudoClassStateChanged(OUT_OF_STOCK, !product.isInStock());
        Tooltip.install(this, new Tooltip(product.getName() + "\nCode " + product.getCode()));
        if (product.isInStock()) {
            this.setCursor(Cursor.HAND);
            this.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    onAdd.accept(product);
                }
            });
        }
    }
}
