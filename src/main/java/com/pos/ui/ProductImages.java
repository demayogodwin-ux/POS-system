package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.ProductImageDao;
import com.pos.model.Product;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/** Loads product photos from the database once per version and shares them between the POS screens. Use on the FX thread. */
final class ProductImages {
    private static final ProductImageDao DAO = new ProductImageDao();
    private static final Map<Integer, Cached> CACHE = new HashMap<>();
    private static final Map<Integer, List<Consumer<Image>>> WAITING = new HashMap<>();

    private record Cached(long version, Image image) {
    }

    private ProductImages() {
    }

    /** Calls back with the product's photo, or null when it has none or it cannot be loaded. */
    static void load(Product product, Consumer<Image> callback) {
        if (!product.hasImage()) {
            callback.accept(null);
            return;
        }

        Cached cached = CACHE.get(product.getId());
        if (cached != null && cached.version() == product.getImageVersion()) {
            callback.accept(cached.image());
            return;
        }

        // Several cells can ask for the same photo at once; fetch it once.
        List<Consumer<Image>> waiting = WAITING.get(product.getId());
        if (waiting != null) {
            waiting.add(callback);
            return;
        }

        waiting = new ArrayList<>();
        waiting.add(callback);
        WAITING.put(product.getId(), waiting);
        int id = product.getId();
        long version = product.getImageVersion();
        Task<Image> task = new Task<Image>() {
            protected Image call() throws Exception {
                return DAO.find(id).map(stored -> new Image(new ByteArrayInputStream(stored.data()))).orElse(null);
            }
        };
        task.setOnSucceeded(e -> finish(id, version, task.getValue()));
        task.setOnFailed(e -> finish(id, version, null));
        Background.run(task);
    }

    private static void finish(int id, long version, Image image) {
        if (image != null) {
            CACHE.put(id, new Cached(version, image));
        }

        List<Consumer<Image>> waiting = WAITING.remove(id);
        if (waiting != null) {
            waiting.forEach(c -> c.accept(image));
        }
    }

    /** A square, rounded thumbnail that shows the first letter of the name until (or unless) a photo loads. */
    static final class Thumbnail extends StackPane {
        private final ImageView view = new ImageView();
        private final Label letter = new Label();
        private Product shown;

        Thumbnail(double size) {
            this.view.setFitWidth(size);
            this.view.setFitHeight(size);
            this.view.setPreserveRatio(false);
            this.view.setSmooth(true);
            Rectangle clip = new Rectangle(size, size);
            clip.setArcWidth(size / 4.0);
            clip.setArcHeight(size / 4.0);
            this.view.setClip(clip);
            this.letter.getStyleClass().add("thumb-letter");
            this.getStyleClass().add("thumb");
            this.setMinSize(size, size);
            this.setPrefSize(size, size);
            this.setMaxSize(size, size);
            this.setAlignment(Pos.CENTER);
            this.getChildren().addAll(this.letter, this.view);
        }

        void show(Product product) {
            this.shown = product;
            this.view.setImage(null);
            this.letter.setText(product == null || product.getName().isEmpty() ? "" : product.getName().substring(0, 1).toUpperCase());
            if (product != null) {
                load(product, image -> {
                    // The cell may have been reused for another product while the photo loaded.
                    if (this.shown == product) {
                        this.view.setImage(image == null ? null : cropSquare(image));
                    }
                });
            }
        }

        void showImage(Image image, String name) {
            this.shown = null;
            this.letter.setText(name == null || name.isBlank() ? "" : name.trim().substring(0, 1).toUpperCase());
            this.view.setImage(image == null ? null : cropSquare(image));
        }

        private Image cropSquare(Image image) {
            // Fill the square like a phone gallery does: crop the middle instead of squashing.
            double side = Math.min(image.getWidth(), image.getHeight());
            this.view.setViewport(new javafx.geometry.Rectangle2D((image.getWidth() - side) / 2.0, (image.getHeight() - side) / 2.0, side, side));
            return image;
        }
    }
}
