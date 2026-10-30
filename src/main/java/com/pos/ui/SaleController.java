package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.CategoryDao;
import com.pos.db.ProductDao;
import com.pos.db.SettingsDao;
import com.pos.model.CartItem;
import com.pos.model.Category;
import com.pos.model.Product;
import com.pos.model.Sale;
import com.pos.model.User;
import com.pos.service.CartService;
import com.pos.service.Money;
import com.pos.service.Session;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import javafx.util.Duration;

public class SaleController {
    private static final Color MUTED = Color.web("#9AA3AE");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    @FXML
    private BorderPane saleRoot;
    @FXML
    private Label cashierLabel;
    @FXML
    private HBox scanBox;
    @FXML
    private TextField scanField;
    @FXML
    private FlowPane categoryBar;
    @FXML
    private Label statusLabel;
    @FXML
    private ScrollPane productScroll;
    @FXML
    private FlowPane productGrid;
    @FXML
    private Label cartTitleLabel;
    @FXML
    private Button clearButton;
    @FXML
    private ListView<CartItem> cartList;
    @FXML
    private Label subtotalLabel;
    @FXML
    private Label taxNameLabel;
    @FXML
    private Label taxLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Button exactButton;
    @FXML
    private Button next100Button;
    @FXML
    private Button next1000Button;
    @FXML
    private Button checkoutButton;
    private final ProductDao productDao = new ProductDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final SettingsDao settingsDao = new SettingsDao();
    private final CartService cart = CartService.getInstance();
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final FilteredList<Product> filteredProducts = new FilteredList(this.products);
    private final SortedList<Product> sortedProducts = new SortedList<>(this.filteredProducts);
    // The grid stays on "Loading products..." until the first load finishes.
    private boolean loaded;
    private final ToggleGroup categoryGroup = new ToggleGroup();
    private final PauseTransition statusTimer = new PauseTransition(Duration.seconds(4.0));
    private final EventHandler<KeyEvent> shortcuts = this::handleShortcut;
    private Integer selectedCategoryId;
    private Map<String, String> settings = Map.of();

    @FXML
    private void initialize() {
        User user = Session.getInstance().getCurrentUser();
        this.cashierLabel.setText("Register 01 - Cashier " + (user == null ? "" : user.getFullName()));
        this.scanBox.getChildren().add(0, Icons.graphic(Icons.Icon.SCAN, MUTED));
        this.scanField.textProperty().addListener((obs, oldText, newText) -> this.applyFilter());
        this.scanField.setOnAction(e -> this.handleScanEnter());
        this.categoryGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
            } else {
                this.selectedCategoryId = (Integer)newToggle.getUserData();
                this.applyFilter();
            }
        });
        this.statusTimer.setOnFinished(e -> this.statusLabel.setVisible(false));
        this.setUpProductGrid();
        this.setUpCart();
        this.setUpShortcuts();
        this.loadData();
        Platform.runLater(this.scanField::requestFocus);
    }

    private void loadData() {
        this.showGridMessage("Loading products...");
        Task<SaleController.SaleData> task = new Task<SaleController.SaleData>() {
            protected SaleController.SaleData call() throws Exception {
                return new SaleController.SaleData(
                    SaleController.this.productDao.findAll(true), SaleController.this.categoryDao.findAll(), SaleController.this.settingsDao.getAll()
                );
            }
        };
        task.setOnSucceeded(e -> {
            SaleController.SaleData data = (SaleController.SaleData)task.getValue();
            this.settings = data.settings();
            this.products.setAll(data.products());
            this.cart.updateStock(data.products());
            this.buildCategoryChips(data.categories());
            this.cart.applyTaxSettings(data.settings().get("tax_rate"), data.settings().get("prices_include_tax"));
            this.loaded = true;
            this.buildGrid();
        });
        task.setOnFailed(e -> {
            this.showGridMessage("Products could not be loaded");
            Alerts.error(this.window(), "Could not load products", task.getException());
        });
        Background.run(task);
    }

    private void buildCategoryChips(List<Category> categories) {
        this.categoryBar.getChildren().clear();
        this.categoryBar.getChildren().add(this.categoryChip("All", null));

        for (Category category : categories) {
            this.categoryBar.getChildren().add(this.categoryChip(category.getName(), category.getId()));
        }

        ((ToggleButton)this.categoryBar.getChildren().get(0)).setSelected(true);
    }

    private ToggleButton categoryChip(String name, Integer categoryId) {
        ToggleButton chip = new ToggleButton(name);
        chip.getStyleClass().add("category-chip");
        chip.setUserData(categoryId);
        chip.setToggleGroup(this.categoryGroup);
        chip.setFocusTraversable(false);
        return chip;
    }

    private void applyFilter() {
        String text = this.scanField.getText().trim().toLowerCase(Locale.ROOT);
        Integer categoryId = this.selectedCategoryId;
        this.filteredProducts
            .setPredicate(
                p -> (categoryId == null || categoryId.equals(p.getCategoryId()))
                    && (text.isEmpty() || p.getCode().toLowerCase(Locale.ROOT).contains(text) || p.getName().toLowerCase(Locale.ROOT).contains(text))
            );
    }

    /** Products show as photo cards sorted by name; the grid follows the search box and category chips. */
    private void setUpProductGrid() {
        this.sortedProducts.setComparator((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        this.sortedProducts.addListener((ListChangeListener<Product>)change -> this.buildGrid());
    }

    private void buildGrid() {
        if (!this.loaded) {
            return;
        }

        if (this.sortedProducts.isEmpty()) {
            this.showGridMessage("No products match your search");
            return;
        }

        List<ProductCard> cards = new ArrayList<>();
        for (Product product : this.sortedProducts) {
            cards.add(new ProductCard(product, this::addToCart));
        }

        this.productGrid.getChildren().setAll(cards);
    }

    private void showGridMessage(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("muted");
        this.productGrid.getChildren().setAll(label);
    }

    private void handleScanEnter() {
        String code = this.scanField.getText().trim();
        if (!code.isEmpty()) {
            this.products
                .stream()
                .filter(p -> p.getCode().equalsIgnoreCase(code))
                .findFirst()
                .ifPresentOrElse(this::addToCart, () -> this.showStatus("No product with code " + code));
            this.scanField.clear();
        }
    }

    private void addToCart(Product product) {
        if (!product.isInStock()) {
            this.showStatus(product.getName() + " is out of stock");
        } else if (!this.cart.add(product, 1)) {
            this.showStatus(stockMessage(product));
        } else {
            if (CartService.parseFlag(this.settings.get("beep_on_scan"))) {
                Beeper.beep();
            }

            this.cart.find(product).ifPresent(item -> {
                this.cartList.getSelectionModel().select(item);
                this.cartList.scrollTo(item);
            });
        }
    }

    private static String stockMessage(Product product) {
        return "Only " + product.getStock() + " of " + product.getName() + " in stock";
    }

    private void showStatus(String message) {
        this.statusLabel.setText(message);
        this.statusLabel.setVisible(true);
        this.statusTimer.playFromStart();
    }

    private void setUpCart() {
        this.cartList.setItems(this.cart.getItems());
        this.cartList.setCellFactory(list -> new CartItemCell(this::incrementItem, this::decrementItem, this::promptQuantity, this.cart::remove));
        Label empty = new Label("Cart is empty.\nScan a barcode or click a product.\nUse - on a line to take one off.");
        this.cartList.setPlaceholder(empty);
        this.cartList.setOnKeyPressed(this::handleCartKey);
        this.cartTitleLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            int count = this.cart.getItemCount();
            return "Cart - " + count + (count == 1 ? " item" : " items");
        }, new Observable[]{this.cart.itemCountProperty()}));
        this.subtotalLabel.textProperty().bind(money(this.cart.subtotalProperty()));
        this.taxLabel.textProperty().bind(money(this.cart.taxProperty()));
        this.totalLabel.textProperty().bind(money(this.cart.totalProperty()));
        this.taxNameLabel
            .textProperty()
            .bind(
                Bindings.createStringBinding(
                    () -> "Tax " + this.cart.taxPercentText() + "%" + (this.cart.pricesIncludeTaxProperty().get() ? " (incl.)" : ""),
                    new Observable[]{this.cart.taxRateProperty(), this.cart.pricesIncludeTaxProperty()}
                )
            );
        this.next100Button
            .textProperty()
            .bind(Bindings.createStringBinding(() -> Money.format(Money.nextStep(this.cart.getTotal(), HUNDRED)), new Observable[]{this.cart.totalProperty()}));
        this.next1000Button
            .textProperty()
            .bind(Bindings.createStringBinding(() -> Money.format(Money.nextStep(this.cart.getTotal(), THOUSAND)), new Observable[]{this.cart.totalProperty()}));
        this.exactButton.setOnAction(e -> this.openCheckout(this.cart.getTotal()));
        this.next100Button.setOnAction(e -> this.openCheckout(Money.nextStep(this.cart.getTotal(), HUNDRED)));
        this.next1000Button.setOnAction(e -> this.openCheckout(Money.nextStep(this.cart.getTotal(), THOUSAND)));
        BooleanBinding cartEmpty = Bindings.isEmpty(this.cart.getItems());

        for (Button button : List.of(this.clearButton, this.exactButton, this.next100Button, this.next1000Button, this.checkoutButton)) {
            button.disableProperty().bind(cartEmpty);
        }
    }

    private static StringBinding money(ObservableValue<BigDecimal> value) {
        return Bindings.createStringBinding(() -> Money.format((BigDecimal)value.getValue()), new Observable[]{value});
    }

    private void handleCartKey(KeyEvent e) {
        CartItem item = (CartItem)this.cartList.getSelectionModel().getSelectedItem();
        if (item != null) {
            switch (e.getCode()) {
                case ADD:
                case PLUS:
                case EQUALS:
                    this.incrementItem(item);
                    break;
                case SUBTRACT:
                case MINUS:
                    this.decrementItem(item);
                    break;
                case DELETE:
                case BACK_SPACE:
                    this.cart.remove(item);
                    break;
                default:
                    return;
            }

            e.consume();
        }
    }

    private void incrementItem(CartItem item) {
        if (!this.cart.increment(item)) {
            this.showStatus(stockMessage(item.getProduct()));
        }
    }

    /** Takes one off; at a quantity of one the line is removed, so a wrong item can be undone without clearing the cart. */
    private void decrementItem(CartItem item) {
        if (item.getQuantity() > 1) {
            this.cart.decrement(item);
        } else {
            this.cart.remove(item);
            this.showStatus(item.getProduct().getName() + " removed");
        }
    }

    private void promptQuantity(CartItem item) {
        int stock = item.getProduct().getStock();
        TextInputDialog dialog = new TextInputDialog(String.valueOf(item.getQuantity()));
        dialog.initOwner(this.window());
        dialog.setTitle("Change quantity");
        dialog.setHeaderText(item.getProduct().getName());
        dialog.setContentText("Quantity (max " + stock + "):");
        dialog.getEditor().setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().matches("\\d{0,6}") ? change : null));
        Alerts.style(dialog);
        dialog.showAndWait().ifPresent(text -> {
            if (!text.isBlank()) {
                int quantity = Integer.parseInt(text);
                if (quantity == 0) {
                    this.cart.remove(item);
                } else if (!this.cart.setQuantity(item, quantity)) {
                    this.showStatus(stockMessage(item.getProduct()));
                }
            }
        });
    }

    @FXML
    private void handleClear() {
        if (!this.cart.isEmpty()) {
            if (Alerts.confirm(this.window(), "Clear the cart?", "All " + this.cart.getItemCount() + " items will be removed.")) {
                this.cart.clear();
            }
        }
    }

    @FXML
    private void handleCheckout() {
        this.openCheckout(null);
    }

    private void openCheckout(BigDecimal tendered) {
        if (!this.cart.isEmpty()) {
            CheckoutController checkout;
            try {
                checkout = CheckoutController.show(this.window(), tendered);
            } catch (RuntimeException | IOException var6) {
                Alerts.error(this.window(), "Could not open Checkout", var6);
                return;
            }

            if (checkout.getStockError() != null) {
                this.refreshProducts();
                Alerts.info(
                    this.window(),
                    checkout.getStockError().getMessage(),
                    "The sale was not saved and the cart was kept. The product list has been refreshed - adjust "
                        + checkout.getStockError().getProductName()
                        + " in the cart and try again."
                );
            } else {
                Sale sale = checkout.getCompletedSale();
                if (sale != null) {
                    try {
                        ReceiptController.show(
                            this.window(), sale, ReceiptView.StoreInfo.fromSettings(this.settings), CartService.parseFlag(this.settings.get("print_receipt"))
                        );
                    } catch (RuntimeException | IOException var5) {
                        Alerts.error(this.window(), "The sale was saved, but the receipt could not be shown", var5);
                    }

                    this.startNewSale();
                }
            }
        }
    }

    private void startNewSale() {
        this.cart.clear();
        this.scanField.clear();
        this.refreshProducts();
        this.scanField.requestFocus();
    }

    private void refreshProducts() {
        Task<List<Product>> task = new Task<List<Product>>() {
            protected List<Product> call() throws Exception {
                return SaleController.this.productDao.findAll(true);
            }
        };
        task.setOnSucceeded(e -> {
            this.products.setAll((Collection)task.getValue());
            this.cart.updateStock((List<Product>)task.getValue());
        });
        task.setOnFailed(e -> Alerts.error(this.window(), "Could not refresh products", task.getException()));
        Background.run(task);
    }

    private void setUpShortcuts() {
        this.saleRoot.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this.shortcuts);
            }

            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this.shortcuts);
            }
        });
    }

    private void handleShortcut(KeyEvent e) {
        switch (e.getCode()) {
            case F2:
                this.scanField.requestFocus();
                this.scanField.selectAll();
                e.consume();
                break;
            case F12:
                e.consume();
                this.handleCheckout();
                break;
            case ESCAPE:
                if (!this.scanField.getText().isEmpty()) {
                    this.scanField.clear();
                    e.consume();
                }
        }
    }

    private Window window() {
        return this.saleRoot.getScene() == null ? null : this.saleRoot.getScene().getWindow();
    }

    private record SaleData(List<Product> products, List<Category> categories, Map<String, String> settings) {
    }
}
