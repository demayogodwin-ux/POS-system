package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.CategoryDao;
import com.pos.db.DbErrors;
import com.pos.db.ProductDao;
import com.pos.db.ProductImageDao;
import com.pos.model.Category;
import com.pos.model.Product;
import com.pos.service.Money;
import com.pos.service.ProductPhotos;
import com.pos.service.ProductService;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

public class ProductsController {
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");
    private static final Color MUTED = Color.web("#9AA3AE");
    private static final Color DANGER = Color.web("#F87171");
    private static final Color ON_ACCENT = Color.web("#1A1300");
    @FXML
    private BorderPane productsRoot;
    @FXML
    private Label subtitleLabel;
    @FXML
    private HBox searchBox;
    @FXML
    private TextField searchField;
    @FXML
    private Button addButton;
    @FXML
    private Label totalProductsLabel;
    @FXML
    private Label categoriesLabel;
    @FXML
    private Label inventoryValueLabel;
    @FXML
    private Label lowStockLabel;
    @FXML
    private Label unitsLabel;
    @FXML
    private TableView<Product> productTable;
    @FXML
    private TableColumn<Product, String> codeColumn;
    @FXML
    private TableColumn<Product, Product> nameColumn;
    @FXML
    private TableColumn<Product, String> categoryColumn;
    @FXML
    private TableColumn<Product, BigDecimal> priceColumn;
    @FXML
    private TableColumn<Product, Integer> stockColumn;
    @FXML
    private TableColumn<Product, Product> statusColumn;
    @FXML
    private TableColumn<Product, Product> actionsColumn;
    @FXML
    private Label panelTitleLabel;
    @FXML
    private HBox photoRow;
    @FXML
    private Button choosePhotoButton;
    @FXML
    private Button removePhotoButton;
    @FXML
    private TextField codeField;
    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<Category> categoryBox;
    @FXML
    private TextField priceField;
    @FXML
    private TextField stockField;
    @FXML
    private TextField levelField;
    @FXML
    private TextField locationField;
    @FXML
    private Label lowStockNote;
    @FXML
    private Label formMessageLabel;
    @FXML
    private Button deleteButton;
    @FXML
    private Button saveButton;
    private final ProductDao productDao = new ProductDao();
    private final ProductImageDao imageDao = new ProductImageDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final ProductImages.Thumbnail photoPreview = new ProductImages.Thumbnail(96.0);
    // A photo picked but not saved yet, or a removal waiting for Save; both are applied with the other fields.
    private byte[] pendingPhoto;
    private boolean removePhoto;
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final FilteredList<Product> filteredProducts = new FilteredList(this.products);
    private final Map<ProductService.Field, Control> fieldControls = new EnumMap<>(ProductService.Field.class);
    private final PauseTransition messageTimer = new PauseTransition(Duration.seconds(3.0));
    private Product editing;
    private boolean busy;

    @FXML
    private void initialize() {
        this.searchBox.getChildren().add(0, Icons.graphic(Icons.Icon.SEARCH, MUTED));
        this.searchField.textProperty().addListener((obs, oldText, newText) -> this.applyFilter());
        this.addButton.setGraphic(Icons.graphic(Icons.Icon.PLUS, ON_ACCENT));
        this.deleteButton.setGraphic(Icons.graphic(Icons.Icon.TRASH, DANGER));
        this.fieldControls.put(ProductService.Field.CODE, this.codeField);
        this.fieldControls.put(ProductService.Field.NAME, this.nameField);
        this.fieldControls.put(ProductService.Field.CATEGORY, this.categoryBox);
        this.fieldControls.put(ProductService.Field.PRICE, this.priceField);
        this.fieldControls.put(ProductService.Field.STOCK, this.stockField);
        this.fieldControls.put(ProductService.Field.LOW_STOCK_LEVEL, this.levelField);
        this.fieldControls.put(ProductService.Field.LOCATION, this.locationField);

        for (Control control : this.fieldControls.values()) {
            control.focusedProperty().addListener((obs, was, focused) -> {
                if (focused) {
                    control.pseudoClassStateChanged(ERROR, false);
                }
            });
        }

        this.lowStockNote.managedProperty().bind(this.lowStockNote.visibleProperty());
        this.stockField.textProperty().addListener((obs, o, n) -> this.updateLowStockNote());
        this.levelField.textProperty().addListener((obs, o, n) -> this.updateLowStockNote());
        this.formMessageLabel.managedProperty().bind(this.formMessageLabel.visibleProperty());
        this.formMessageLabel.setVisible(false);
        this.messageTimer.setOnFinished(e -> this.formMessageLabel.setVisible(false));
        this.photoRow.getChildren().add(0, this.photoPreview);
        this.setUpTable();
        this.startNew();
        this.loadData(null, null);
    }

    private void loadData(Integer selectId, String successMessage) {
        this.productTable.setPlaceholder(new Label("Loading products..."));
        Task<ProductsController.ProductsData> task = new Task<ProductsController.ProductsData>() {
            protected ProductsController.ProductsData call() throws Exception {
                return new ProductsController.ProductsData(
                    ProductsController.this.productDao.findAll(true),
                    ProductsController.this.categoryDao.findAll(),
                    ProductsController.this.productDao.countLowStock(),
                    ProductsController.this.productDao.inventoryValue(),
                    ProductsController.this.productDao.totalUnits()
                );
            }
        };
        task.setOnSucceeded(e -> {
            ProductsController.ProductsData data = (ProductsController.ProductsData)task.getValue();
            Category selectedCategory = (Category)this.categoryBox.getValue();
            this.categoryBox.getItems().setAll(data.categories());
            this.categoryBox.setValue(this.findCategory(selectedCategory == null ? null : selectedCategory.getId()));
            this.products.setAll(data.products());
            this.updateStats(data);
            this.productTable.setPlaceholder(new Label("No products match your search"));
            if (selectId != null) {
                this.products.stream().filter(p -> p.getId() == selectId).findFirst().ifPresent(p -> {
                    this.productTable.getSelectionModel().select(p);
                    this.productTable.scrollTo(p);
                });
            }

            if (successMessage != null) {
                this.showMessage(successMessage, false);
            }
        });
        task.setOnFailed(e -> {
            this.productTable.setPlaceholder(new Label("Products could not be loaded"));
            Alerts.error(this.window(), "Could not load products", task.getException());
        });
        Background.run(task);
    }

    private void updateStats(ProductsController.ProductsData data) {
        int count = data.products().size();
        this.subtitleLabel.setText(count + (count == 1 ? " item" : " items") + " - " + data.lowStock() + " low stock");
        this.totalProductsLabel.setText(String.valueOf(count));
        int categories = data.categories().size();
        this.categoriesLabel.setText("Across " + categories + (categories == 1 ? " category" : " categories"));
        this.inventoryValueLabel.setText(Money.currency(data.inventoryValue()));
        this.lowStockLabel.setText(String.valueOf(data.lowStock()));
        this.unitsLabel.setText(String.format(Locale.US, "%,d", data.units()));
    }

    private Category findCategory(Integer categoryId) {
        return categoryId == null ? null : this.categoryBox.getItems().stream().filter(c -> c.getId() == categoryId).findFirst().orElse(null);
    }

    private void applyFilter() {
        String text = this.searchField.getText().trim().toLowerCase(Locale.ROOT);
        this.filteredProducts
            .setPredicate(
                p -> text.isEmpty()
                    || p.getCode().toLowerCase(Locale.ROOT).contains(text)
                    || p.getName().toLowerCase(Locale.ROOT).contains(text)
                    || p.getCategoryName() != null && p.getCategoryName().toLowerCase(Locale.ROOT).contains(text)
                    || p.getLocation() != null && p.getLocation().toLowerCase(Locale.ROOT).contains(text)
            );
    }

    private void setUpTable() {
        this.codeColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(((Product)cd.getValue()).getCode()));
        this.nameColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        this.nameColumn.setCellFactory(col -> new ProductNameCell());
        this.productTable.setFixedCellSize(ProductNameCell.ROW_HEIGHT);
        this.nameColumn.setComparator((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        this.categoryColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Objects.requireNonNullElse(((Product)cd.getValue()).getCategoryName(), "")));
        this.priceColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper(((Product)cd.getValue()).getPrice()));
        this.priceColumn.setCellFactory(col -> textCell(Money::format));
        this.stockColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper(((Product)cd.getValue()).getStock()));
        this.stockColumn.setCellFactory(col -> textCell(String::valueOf));
        this.statusColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper((Product)cd.getValue()));
        this.statusColumn.setCellFactory(col -> new ProductsController.StatusCell());
        this.statusColumn.setComparator((a, b) -> Integer.compare(statusRank(a), statusRank(b)));
        this.actionsColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper((Product)cd.getValue()));
        this.actionsColumn.setCellFactory(col -> new ProductsController.ActionsCell());
        // Narrow enough that names with their photo thumbnail are not cut short on a laptop screen.
        fixWidth(this.codeColumn, 120.0);
        fixWidth(this.categoryColumn, 120.0);
        fixWidth(this.priceColumn, 110.0);
        fixWidth(this.stockColumn, 80.0);
        fixWidth(this.statusColumn, 130.0);
        fixWidth(this.actionsColumn, 100.0);
        this.productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        SortedList<Product> sorted = new SortedList(this.filteredProducts);
        sorted.comparatorProperty().bind(this.productTable.comparatorProperty());
        this.productTable.setItems(sorted);
        this.productTable.getSelectionModel().selectedItemProperty().addListener((obs, oldProduct, product) -> {
            if (product != null) {
                this.editProduct(product);
            }
        });
    }

    private static void fixWidth(TableColumn<?, ?> column, double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
    }

    private static <T> TableCell<Product, T> textCell(final Function<T, String> format) {
        return new TableCell<Product, T>() {
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                this.setText(!empty && item != null ? format.apply(item) : null);
            }
        };
    }

    private static int statusRank(Product p) {
        if (!p.isInStock()) {
            return 0;
        } else {
            return p.isLowStock() ? 1 : 2;
        }
    }

    @FXML
    private void handleAdd() {
        this.productTable.getSelectionModel().clearSelection();
        this.startNew();
        this.codeField.requestFocus();
    }

    private void startNew() {
        this.editing = null;
        this.panelTitleLabel.setText("New Product");
        this.codeField.clear();
        this.nameField.clear();
        this.categoryBox.setValue(null);
        this.priceField.clear();
        this.stockField.setText("0");
        this.levelField.setText("5");
        this.locationField.clear();
        this.resetPhoto(null);
        this.deleteButton.setVisible(false);
        this.deleteButton.setManaged(false);
        this.clearFormState();
    }

    private void editProduct(Product product) {
        this.editing = product;
        this.panelTitleLabel.setText("Edit Product");
        this.codeField.setText(product.getCode());
        this.nameField.setText(product.getName());
        this.categoryBox.setValue(this.findCategory(product.getCategoryId()));
        this.priceField.setText(product.getPrice().toPlainString());
        this.stockField.setText(String.valueOf(product.getStock()));
        this.levelField.setText(String.valueOf(product.getLowStockLevel()));
        this.locationField.setText(Objects.requireNonNullElse(product.getLocation(), ""));
        this.resetPhoto(product);
        this.deleteButton.setVisible(true);
        this.deleteButton.setManaged(true);
        this.clearFormState();
    }

    private void clearFormState() {
        for (Node control : this.fieldControls.values()) {
            control.pseudoClassStateChanged(ERROR, false);
        }

        this.formMessageLabel.setVisible(false);
        this.updateLowStockNote();
    }

    private void updateLowStockNote() {
        Integer stock = ProductService.tryParseWholeNumber(this.stockField.getText());
        Integer level = ProductService.tryParseWholeNumber(this.levelField.getText());
        this.lowStockNote.setVisible(stock != null && level != null && stock <= level);
    }

    @FXML
    private void handleSave() {
        if (!this.busy) {
            int id = this.editing == null ? 0 : this.editing.getId();

            final Product product;
            try {
                product = ProductService.validate(
                    id,
                    this.codeField.getText(),
                    this.nameField.getText(),
                    (Category)this.categoryBox.getValue(),
                    this.priceField.getText(),
                    this.stockField.getText(),
                    this.levelField.getText(),
                    this.locationField.getText(),
                    this.products
                );
            } catch (ProductService.ValidationException var5) {
                this.showFieldError(var5.getField(), var5.getMessage());
                return;
            }

            this.setBusy(true);
            final boolean isNew = this.editing == null;
            final byte[] photo = this.pendingPhoto;
            final boolean dropPhoto = this.removePhoto;
            Task<Integer> task = new Task<Integer>() {
                protected Integer call() throws Exception {
                    int savedId;
                    if (isNew) {
                        savedId = ProductsController.this.productDao.insert(product);
                    } else {
                        ProductsController.this.productDao.update(product);
                        savedId = product.getId();
                    }

                    if (photo != null) {
                        ProductsController.this.imageDao.save(savedId, ProductPhotos.CONTENT_TYPE, photo);
                    } else if (dropPhoto) {
                        ProductsController.this.imageDao.delete(savedId);
                    }

                    return savedId;
                }
            };
            task.setOnSucceeded(e -> {
                this.setBusy(false);
                this.loadData((Integer)task.getValue(), isNew ? "Product added" : "Changes saved");
            });
            task.setOnFailed(e -> {
                this.setBusy(false);
                Throwable error = task.getException();
                if (DbErrors.isDuplicateKey(error)) {
                    this.showFieldError(ProductService.Field.CODE, "This code is already used");
                } else {
                    Alerts.error(this.window(), "The product was not saved", error);
                }
            });
            Background.run(task);
        }
    }

    /** Shows the saved photo of the product being edited (none for a new one) and forgets unsaved photo changes. */
    private void resetPhoto(Product product) {
        this.pendingPhoto = null;
        this.removePhoto = false;
        if (product == null) {
            this.photoPreview.showImage(null, "");
        } else {
            this.photoPreview.show(product);
        }

        this.removePhotoButton.setDisable(product == null || !product.hasImage());
    }

    @FXML
    private void handleChoosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a photo of the product");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"));
        File file = chooser.showOpenDialog(this.window());
        if (file == null) {
            return;
        }

        this.choosePhotoButton.setDisable(true);
        Task<byte[]> task = new Task<byte[]>() {
            protected byte[] call() throws Exception {
                return ProductPhotos.prepare(file);
            }
        };
        task.setOnSucceeded(e -> {
            this.choosePhotoButton.setDisable(false);
            this.pendingPhoto = task.getValue();
            this.removePhoto = false;
            this.photoPreview.showImage(new Image(new ByteArrayInputStream(this.pendingPhoto)), this.nameField.getText());
            this.removePhotoButton.setDisable(false);
            this.showMessage("Photo ready - press " + this.saveButton.getText() + " to keep it", false);
        });
        task.setOnFailed(e -> {
            this.choosePhotoButton.setDisable(false);
            Alerts.error(this.window(), "The photo could not be used", task.getException());
        });
        Background.run(task);
    }

    @FXML
    private void handleRemovePhoto() {
        this.pendingPhoto = null;
        this.removePhoto = this.editing != null && this.editing.hasImage();
        this.photoPreview.showImage(null, this.nameField.getText());
        this.removePhotoButton.setDisable(true);
        if (this.removePhoto) {
            this.showMessage("Photo will be removed when you press " + this.saveButton.getText(), false);
        }
    }

    @FXML
    private void handleDeleteSelected() {
        if (this.editing != null) {
            this.confirmDelete(this.editing);
        }
    }

    private void confirmDelete(final Product product) {
        if (!this.busy) {
            boolean confirmed = Alerts.confirm(
                this.window(), "Delete " + product.getName() + "?", "It will be removed from the product list and the Sale screen. Past sales are kept."
            );
            if (confirmed) {
                this.setBusy(true);
                Task<Void> task = new Task<Void>() {
                    protected Void call() throws Exception {
                        ProductsController.this.productDao.softDelete(product.getId());
                        return null;
                    }
                };
                task.setOnSucceeded(e -> {
                    this.setBusy(false);
                    this.productTable.getSelectionModel().clearSelection();
                    this.startNew();
                    this.loadData(null, product.getName() + " deleted");
                });
                task.setOnFailed(e -> {
                    this.setBusy(false);
                    Alerts.error(this.window(), "The product was not deleted", task.getException());
                });
                Background.run(task);
            }
        }
    }

    private void setBusy(boolean value) {
        this.busy = value;
        this.saveButton.setDisable(value);
        this.deleteButton.setDisable(value);
        this.saveButton.setText(value ? "Saving..." : "Save Changes");
    }

    private void showFieldError(ProductService.Field field, String message) {
        Control control = this.fieldControls.get(field);
        if (control != null) {
            control.requestFocus();
            Platform.runLater(() -> control.pseudoClassStateChanged(ERROR, true));
        }

        this.showMessage(message, true);
    }

    private void showMessage(String message, boolean isError) {
        this.formMessageLabel.setText(message);
        this.formMessageLabel.pseudoClassStateChanged(ERROR, isError);
        this.formMessageLabel.pseudoClassStateChanged(SUCCESS, !isError);
        this.formMessageLabel.setVisible(true);
        if (isError) {
            this.messageTimer.stop();
        } else {
            this.messageTimer.playFromStart();
        }
    }

    private Window window() {
        return this.productsRoot.getScene() == null ? null : this.productsRoot.getScene().getWindow();
    }

    private class ActionsCell extends TableCell<Product, Product> {
        private final Button edit = new Button(null, Icons.graphic(Icons.Icon.EDIT, ProductsController.MUTED));
        private final Button delete = new Button(null, Icons.graphic(Icons.Icon.TRASH, ProductsController.DANGER));
        private final HBox box = new HBox(4.0, new Node[]{this.edit, this.delete});

        ActionsCell() {
            this.edit.getStyleClass().addAll(new String[]{"btn-icon", "btn-icon-sm"});
            this.delete.getStyleClass().addAll(new String[]{"btn-icon", "btn-icon-sm", "btn-icon-danger"});
            this.edit.setTooltip(new Tooltip("Edit"));
            this.delete.setTooltip(new Tooltip("Delete"));
            this.edit.setFocusTraversable(false);
            this.delete.setFocusTraversable(false);
            this.box.setAlignment(Pos.CENTER_LEFT);
            this.edit.setOnAction(e -> {
                Product product = (Product)this.getItem();
                if (product != null) {
                    ProductsController.this.productTable.getSelectionModel().select(product);
                    ProductsController.this.editProduct(product);
                    ProductsController.this.nameField.requestFocus();
                }
            });
            this.delete.setOnAction(e -> {
                Product product = (Product)this.getItem();
                if (product != null) {
                    ProductsController.this.confirmDelete(product);
                }
            });
        }

        protected void updateItem(Product product, boolean empty) {
            super.updateItem(product, empty);
            this.setText(null);
            this.setGraphic(!empty && product != null ? this.box : null);
        }
    }

    private record ProductsData(List<Product> products, List<Category> categories, int lowStock, BigDecimal inventoryValue, long units) {
    }

    private static class StatusCell extends TableCell<Product, Product> {
        private final Label chip = new Label();

        protected void updateItem(Product product, boolean empty) {
            super.updateItem(product, empty);
            this.setText(null);
            if (!empty && product != null) {
                switch (ProductsController.statusRank(product)) {
                    case 0:
                        this.setChip("Out of stock", "chip-danger");
                        break;
                    case 1:
                        this.setChip("Low stock", "chip-warning");
                        break;
                    default:
                        this.setChip("In stock", "chip-success");
                }

                this.setGraphic(this.chip);
            } else {
                this.setGraphic(null);
            }
        }

        private void setChip(String text, String styleClass) {
            this.chip.setText(text);
            this.chip.getStyleClass().setAll(new String[]{"label", styleClass});
        }
    }
}
