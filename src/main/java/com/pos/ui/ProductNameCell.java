package com.pos.ui;

import com.pos.model.Product;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;

/** Product name with its photo thumbnail, for the Products and Sale tables. */
class ProductNameCell extends TableCell<Product, Product> {
    /** Row height for tables using this cell: fits the thumbnail and two lines of name. A fixed height keeps scrolling steady. */
    static final double ROW_HEIGHT = 52.0;
    private static final double SIZE = 36.0;
    private final ProductImages.Thumbnail thumbnail = new ProductImages.Thumbnail(SIZE);
    private final Label name = new Label();
    private final HBox box = new HBox(10.0, this.thumbnail, this.name);

    ProductNameCell() {
        this.box.setAlignment(Pos.CENTER_LEFT);
        this.name.setMinWidth(0.0);
        // Wrap rather than cut off, so "(slice)" and "(whole, 8 in)" stay visible.
        this.name.setWrapText(true);
    }

    protected void updateItem(Product product, boolean empty) {
        super.updateItem(product, empty);
        this.setText(null);
        if (!empty && product != null) {
            this.name.setText(product.getName());
            this.thumbnail.show(product);
            this.setGraphic(this.box);
        } else {
            this.thumbnail.show(null);
            this.setGraphic(null);
        }
    }
}
