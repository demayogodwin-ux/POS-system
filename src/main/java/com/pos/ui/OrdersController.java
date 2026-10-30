package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.OnlineOrderDao;
import com.pos.db.SaleDao;
import com.pos.model.OnlineOrder;
import com.pos.model.OnlineOrderItem;
import com.pos.model.OrderStatus;
import com.pos.model.OrderType;
import com.pos.model.Sale;
import com.pos.model.User;
import com.pos.service.Money;
import com.pos.service.OnlineOrderService;
import com.pos.service.Session;
import com.pos.web.MobileServer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;

public class OrdersController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final Duration REFRESH_EVERY = Duration.seconds(10.0);
    @FXML
    private BorderPane ordersRoot;
    @FXML
    private Label serverLabel;
    @FXML
    private DatePicker datePicker;
    @FXML
    private ToggleButton allDaysToggle;
    @FXML
    private Button qrCodeButton;
    @FXML
    private Button copyAppLinkButton;
    @FXML
    private Label newLabel;
    @FXML
    private Label inProgressLabel;
    @FXML
    private Label completedLabel;
    @FXML
    private Label completedNote;
    @FXML
    private TableView<OnlineOrder> ordersTable;
    @FXML
    private TableColumn<OnlineOrder, String> orderColumn;
    @FXML
    private TableColumn<OnlineOrder, String> timeColumn;
    @FXML
    private TableColumn<OnlineOrder, String> customerColumn;
    @FXML
    private TableColumn<OnlineOrder, String> typeColumn;
    @FXML
    private TableColumn<OnlineOrder, BigDecimal> totalColumn;
    @FXML
    private TableColumn<OnlineOrder, OrderStatus> statusColumn;
    @FXML
    private TableColumn<OnlineOrder, Integer> chatColumn;
    @FXML
    private Button chatButton;
    @FXML
    private Label emptyDetailLabel;
    @FXML
    private VBox detailBox;
    @FXML
    private Label detailTitleLabel;
    @FXML
    private Label detailStatusChip;
    @FXML
    private Label detailCustomerLabel;
    @FXML
    private Label detailMetaLabel;
    @FXML
    private Label detailWhereLabel;
    @FXML
    private Label detailNoteLabel;
    @FXML
    private HBox riderRow;
    @FXML
    private Label riderLabel;
    @FXML
    private VBox itemsBox;
    @FXML
    private Label detailSubtotalLabel;
    @FXML
    private Label detailTaxLabel;
    @FXML
    private Label detailTotalCaption;
    @FXML
    private Label detailTotalLabel;
    @FXML
    private Button advanceButton;
    @FXML
    private Button cancelButton;
    private final OnlineOrderDao orderDao = new OnlineOrderDao();
    private final SaleDao saleDao = new SaleDao();
    private final ObservableList<OnlineOrder> orders = FXCollections.observableArrayList();
    private final Timeline refresher = new Timeline(new KeyFrame(REFRESH_EVERY, e -> this.load(null)));
    private final Set<Integer> knownPending = new HashSet<>();
    private final Map<Integer, Integer> knownUnread = new HashMap<>();
    // Items never change after an order is placed, so they are fetched once per order.
    private final Map<Integer, List<OnlineOrderItem>> itemCache = new HashMap<>();
    private boolean firstLoad = true;
    private boolean loading;
    private boolean busy;
    private OnlineOrder shownOrder;

    @FXML
    private void initialize() {
        this.detailBox.managedProperty().bind(this.detailBox.visibleProperty());
        this.emptyDetailLabel.visibleProperty().bind(this.detailBox.visibleProperty().not());
        this.riderRow.managedProperty().bind(this.riderRow.visibleProperty());
        this.detailNoteLabel.managedProperty().bind(this.detailNoteLabel.visibleProperty());
        this.setUpTable();
        this.setUpDateFilter();
        this.showServerAddress();
        // Keep polling while this screen is on display; stop once the shell swaps it out.
        this.refresher.setCycleCount(Timeline.INDEFINITE);
        this.ordersRoot.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) {
                this.refresher.stop();
            }
        });
        this.ordersRoot.parentProperty().addListener((obs, oldParent, parent) -> {
            if (parent == null) {
                this.refresher.stop();
            }
        });
        this.refresher.play();
        this.load(null);
    }

    private void showServerAddress() {
        if (MobileServer.isRunning()) {
            List<String> urls = MobileServer.addresses();
            this.serverLabel.setText("Customers open " + String.join("  or  ", urls) + " on their phone (same Wi-Fi)");
            this.copyAppLinkButton.setDisable(false);
            this.qrCodeButton.setDisable(false);
        } else {
            this.serverLabel.setText("The phone app server is not running. Check the database connection and restart the POS.");
            this.copyAppLinkButton.setDisable(true);
            this.qrCodeButton.setDisable(true);
        }
    }

    /** Open orders always show; finished ones show for the picked day, or for every day with "All days". */
    private void setUpDateFilter() {
        this.datePicker.setConverter(new StringConverter<LocalDate>() {
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(SHORT_DATE);
            }

            public LocalDate fromString(String text) {
                return text != null && !text.isBlank() ? LocalDate.parse(text, SHORT_DATE) : null;
            }
        });
        this.datePicker.setValue(LocalDate.now());
        this.datePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate == null) {
                this.datePicker.setValue(oldDate);
            } else {
                this.load(null);
            }
        });
        this.datePicker.disableProperty().bind(this.allDaysToggle.selectedProperty());
        this.allDaysToggle.selectedProperty().addListener((obs, was, all) -> this.load(null));
    }

    private void load(Integer selectId) {
        if (!this.loading) {
            this.loading = true;
            Integer keepId = selectId != null ? selectId : (this.shownOrder == null ? null : this.shownOrder.getId());
            final boolean allDays = this.allDaysToggle.isSelected();
            final LocalDate day = this.datePicker.getValue();
            Task<List<OnlineOrder>> task = new Task<List<OnlineOrder>>() {
                protected List<OnlineOrder> call() throws Exception {
                    return allDays ? OrdersController.this.orderDao.findAllForStaff() : OrdersController.this.orderDao.findForStaff(day);
                }
            };
            task.setOnSucceeded(e -> {
                this.loading = false;
                List<OnlineOrder> fresh = task.getValue();
                this.notifyNewOrders(fresh);
                this.orders.setAll(fresh);
                this.showStats(fresh);
                this.ordersTable.setPlaceholder(new Label(
                    allDays ? "No online orders yet" : day.equals(LocalDate.now()) ? "No online orders yet today" : "No online orders on " + day.format(SHORT_DATE)
                ));
                OnlineOrder reselect = keepId == null ? null : fresh.stream().filter(o -> o.getId() == keepId).findFirst().orElse(null);
                if (reselect != null) {
                    this.ordersTable.getSelectionModel().select(reselect);
                } else {
                    this.showDetail(null);
                }

                if (!MobileServer.isRunning() || this.serverLabel.getText().contains("not running")) {
                    this.showServerAddress();
                }
            });
            task.setOnFailed(e -> {
                this.loading = false;
                this.ordersTable.setPlaceholder(new Label("Online orders could not be loaded"));
            });
            Background.run(task);
        }
    }

    /** Beeps when an order or a customer chat message arrives that was not there on the previous refresh. */
    private void notifyNewOrders(List<OnlineOrder> fresh) {
        boolean arrived = false;

        for (OnlineOrder order : fresh) {
            if (order.getStatus() == OrderStatus.PENDING && this.knownPending.add(order.getId()) && !this.firstLoad) {
                arrived = true;
            }

            Integer before = this.knownUnread.put(order.getId(), order.getUnreadByStaff());
            if (!this.firstLoad && order.getUnreadByStaff() > (before == null ? 0 : before)) {
                arrived = true;
            }
        }

        this.firstLoad = false;
        if (arrived) {
            Beeper.beep();
        }
    }

    private void showStats(List<OnlineOrder> fresh) {
        LocalDate today = LocalDate.now();
        long pending = fresh.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
        long inProgress = fresh.stream().filter(o -> o.getStatus().isOpen() && o.getStatus() != OrderStatus.PENDING).count();
        List<OnlineOrder> completed = fresh.stream()
            .filter(o -> o.getStatus() == OrderStatus.COMPLETED && o.getUpdatedAt() != null && o.getUpdatedAt().toLocalDate().equals(today))
            .toList();
        BigDecimal value = completed.stream().map(OnlineOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.newLabel.setText(String.valueOf(pending));
        this.inProgressLabel.setText(String.valueOf(inProgress));
        this.completedLabel.setText(String.valueOf(completed.size()));
        this.completedNote.setText(Money.currency(value) + " recorded as sales");
    }

    private void setUpTable() {
        this.orderColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getOrderNo()));
        this.timeColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getCreatedAt().format(TIME)));
        this.customerColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getCustomerName()));
        this.typeColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getType().label()));
        this.totalColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getTotal()));
        this.totalColumn.setCellFactory(col -> textCell(Money::currency));
        this.statusColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getStatus()));
        this.statusColumn.setCellFactory(col -> new TableCell<OnlineOrder, OrderStatus>() {
            private final Label chip = new Label();

            protected void updateItem(OrderStatus status, boolean empty) {
                super.updateItem(status, empty);
                this.setText(null);
                if (!empty && status != null) {
                    OrdersController.applyStatusChip(this.chip, status);
                    this.setGraphic(this.chip);
                } else {
                    this.setGraphic(null);
                }
            }
        });
        // Fixed columns are kept narrow so CUSTOMER has room and CHAT stays on screen on a laptop.
        fixWidth(this.orderColumn, 85.0);
        fixWidth(this.timeColumn, 140.0);
        fixWidth(this.typeColumn, 80.0);
        fixWidth(this.totalColumn, 105.0);
        fixWidth(this.statusColumn, 115.0);
        this.chatColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getUnreadByStaff()));
        this.chatColumn.setCellFactory(col -> new TableCell<OnlineOrder, Integer>() {
            private final Label chip = new Label();

            protected void updateItem(Integer unread, boolean empty) {
                super.updateItem(unread, empty);
                this.setText(null);
                if (!empty && unread != null && unread > 0) {
                    this.chip.setText(unread + " new");
                    this.chip.getStyleClass().setAll("label", "chip-warning");
                    this.setGraphic(this.chip);
                } else {
                    this.setGraphic(null);
                }
            }
        });
        fixWidth(this.chatColumn, 75.0);
        this.ordersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        this.ordersTable.setItems(this.orders);
        this.ordersTable.setPlaceholder(new Label("Loading online orders..."));
        this.ordersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldOrder, order) -> this.showDetail(order));
    }

    private static void fixWidth(TableColumn<?, ?> column, double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
    }

    private static <T> TableCell<OnlineOrder, T> textCell(final Function<T, String> format) {
        return new TableCell<OnlineOrder, T>() {
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                this.setText(!empty && item != null ? format.apply(item) : null);
            }
        };
    }

    private static void applyStatusChip(Label chip, OrderStatus status) {
        String styleClass = switch (status) {
            case PENDING -> "chip-warning";
            case PREPARING, READY, OUT_FOR_DELIVERY -> "chip-neutral";
            case COMPLETED -> "chip-success";
            case CANCELLED -> "chip-danger";
        };
        chip.setText(status.label());
        chip.getStyleClass().setAll("label", styleClass);
    }

    private void showDetail(final OnlineOrder order) {
        this.shownOrder = order;
        if (order == null) {
            this.detailBox.setVisible(false);
            return;
        }

        this.detailBox.setVisible(true);
        this.detailTitleLabel.setText("Order " + order.getOrderNo());
        applyStatusChip(this.detailStatusChip, order.getStatus());
        this.detailCustomerLabel.setText(order.getCustomerName() + "  -  " + order.getCustomerPhone());
        this.detailMetaLabel.setText(order.getCreatedAt().format(TIME) + " - " + order.getStoreName() + " - pays by " + order.getPaymentMethod().label());
        if (order.getType() == OrderType.DELIVERY) {
            String pin = order.getDeliveryLat() == null ? "" : " (pin dropped on map)";
            this.detailWhereLabel.setText("Deliver to: " + order.getDeliveryAddress() + pin);
        } else {
            this.detailWhereLabel.setText("In store: " + order.getInStoreSpot());
        }

        this.detailNoteLabel.setText(order.getNote() == null ? "" : "Note: " + order.getNote());
        this.detailNoteLabel.setVisible(order.getNote() != null);
        boolean showRider = order.getType() == OrderType.DELIVERY && order.getStatus().isOpen() && MobileServer.isRunning();
        this.riderRow.setVisible(showRider);
        if (showRider) {
            String gps = order.getRiderUpdatedAt() == null ? "no GPS from the rider yet" : "rider GPS last sent " + order.getRiderUpdatedAt().format(TIME);
            this.riderLabel.setText("Rider code " + order.getRiderCode() + " - " + gps);
        }

        int unread = order.getUnreadByStaff();
        this.chatButton.setText(unread > 0 ? "Chat with customer (" + unread + " new)" : "Chat with customer");
        this.detailSubtotalLabel.setText(Money.currency(order.getSubtotal()));
        this.detailTaxLabel.setText(Money.currency(order.getTax()));
        this.detailTotalCaption.setText(order.getStatus() == OrderStatus.COMPLETED ? "Total (paid)" : "Total to collect");
        this.detailTotalLabel.setText(Money.currency(order.getTotal()));
        this.updateButtons();
        List<OnlineOrderItem> cached = this.itemCache.get(order.getId());
        if (cached != null) {
            order.setItems(cached);
            this.showItems(order);
        } else {
            this.itemsBox.getChildren().setAll(new Label("Loading items..."));
            Task<List<OnlineOrderItem>> task = new Task<List<OnlineOrderItem>>() {
                protected List<OnlineOrderItem> call() throws Exception {
                    return OrdersController.this.orderDao.findItems(order.getId());
                }
            };
            task.setOnSucceeded(e -> {
                order.setItems(task.getValue());
                this.itemCache.put(order.getId(), task.getValue());
                if (this.shownOrder == order) {
                    this.showItems(order);
                }
            });
            task.setOnFailed(e -> {
                if (this.shownOrder == order) {
                    this.itemsBox.getChildren().setAll(new Label("Items could not be loaded"));
                }
            });
            Background.run(task);
        }
    }

    private void showItems(OnlineOrder order) {
        this.itemsBox.getChildren().clear();

        for (OnlineOrderItem item : order.getItems()) {
            Label quantity = new Label(item.quantity() + "x");
            quantity.getStyleClass().addAll("cart-qty", "mono");
            Label name = new Label(item.productName());
            name.getStyleClass().add("cart-name");
            name.setMinWidth(0.0);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            Label total = new Label(Money.currency(item.lineTotal()));
            total.getStyleClass().addAll("cart-line-total", "mono");
            total.setMinWidth(Double.NEGATIVE_INFINITY);
            HBox row = new HBox(12.0, new Node[]{quantity, name, total});
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("cart-row");
            this.itemsBox.getChildren().add(row);
        }
    }

    private void updateButtons() {
        OnlineOrder order = this.shownOrder;
        OrderStatus next = order == null ? null : order.getStatus().next(order.getType());
        this.advanceButton.setVisible(next != null);
        this.advanceButton.setManaged(next != null);
        this.advanceButton.setDisable(this.busy);
        if (next != null) {
            this.advanceButton.setText(switch (next) {
                case PREPARING -> "Accept order";
                case READY -> "Mark ready - bring to customer";
                case OUT_FOR_DELIVERY -> "Send out for delivery";
                case COMPLETED -> order.getType() == OrderType.DELIVERY ? "Delivered and paid" : "Handed over and paid";
                default -> next.label();
            });
        }

        boolean canCancel = order != null && order.getStatus().isOpen();
        this.cancelButton.setVisible(canCancel);
        this.cancelButton.setManaged(canCancel);
        this.cancelButton.setDisable(this.busy);
    }

    @FXML
    private void handleAdvance() {
        final OnlineOrder order = this.shownOrder;
        if (order != null && !this.busy) {
            final OrderStatus from = order.getStatus();
            final OrderStatus to = from.next(order.getType());
            if (to != null) {
                if (to == OrderStatus.COMPLETED) {
                    boolean confirmed = Alerts.confirm(
                        this.window(),
                        "Complete order " + order.getOrderNo() + "?",
                        "Confirm the customer received the items and paid " + Money.currency(order.getTotal())
                            + " (" + order.getPaymentMethod().label() + "). It will be recorded as a sale and taken out of stock."
                    );
                    if (!confirmed) {
                        return;
                    }
                }

                final User staff = Session.getInstance().getCurrentUser();
                this.setBusy(true);
                Task<Boolean> task = new Task<Boolean>() {
                    protected Boolean call() throws Exception {
                        if (to != OrderStatus.COMPLETED) {
                            return OrdersController.this.orderDao.changeStatus(order.getId(), from, to, null);
                        }

                        OnlineOrder full = OrdersController.this.orderDao.findById(order.getId()).orElseThrow();
                        Sale sale = OrdersController.this.saleDao.save(OnlineOrderService.toSale(full, staff));
                        if (OrdersController.this.orderDao.changeStatus(order.getId(), from, to, sale.getId())) {
                            return true;
                        }

                        // The customer or another till changed the order meanwhile: undo the sale.
                        OrdersController.this.saleDao.voidSale(sale.getId());
                        return false;
                    }
                };
                task.setOnSucceeded(e -> {
                    this.setBusy(false);
                    if (!task.getValue()) {
                        Alerts.info(this.window(), "Order " + order.getOrderNo() + " changed", "It was updated somewhere else (maybe cancelled by the customer). The list has been refreshed.");
                    }

                    this.load(order.getId());
                });
                task.setOnFailed(e -> {
                    this.setBusy(false);
                    Alerts.error(this.window(), "Order " + order.getOrderNo() + " was not updated", task.getException());
                    this.load(order.getId());
                });
                Background.run(task);
            }
        }
    }

    @FXML
    private void handleCancel() {
        final OnlineOrder order = this.shownOrder;
        if (order != null && !this.busy && order.getStatus().isOpen()) {
            boolean confirmed = Alerts.confirm(
                this.window(), "Cancel order " + order.getOrderNo() + "?", "The customer will see it as cancelled in the app. Call them on " + order.getCustomerPhone() + " to explain why."
            );
            if (confirmed) {
                final OrderStatus from = order.getStatus();
                this.setBusy(true);
                Task<Boolean> task = new Task<Boolean>() {
                    protected Boolean call() throws Exception {
                        return OrdersController.this.orderDao.changeStatus(order.getId(), from, OrderStatus.CANCELLED, null);
                    }
                };
                task.setOnSucceeded(e -> {
                    this.setBusy(false);
                    this.load(order.getId());
                });
                task.setOnFailed(e -> {
                    this.setBusy(false);
                    Alerts.error(this.window(), "Order " + order.getOrderNo() + " was not cancelled", task.getException());
                });
                Background.run(task);
            }
        }
    }

    @FXML
    private void handleRefresh() {
        this.load(null);
    }

    @FXML
    private void handleChat() {
        if (this.shownOrder != null) {
            OrderChatWindow.open(this.window(), this.shownOrder, () -> this.load(null));
        }
    }

    @FXML
    private void handleShowQrCode() {
        QrCodeDialog.show(this.window(), "Scan to order from your phone", MobileServer.addresses().get(0));
    }

    @FXML
    private void handleCopyAppLink() {
        copy(MobileServer.addresses().get(0));
    }

    @FXML
    private void handleCopyRiderLink() {
        if (this.shownOrder != null) {
            copy(MobileServer.addresses().get(0) + "#/rider/" + this.shownOrder.getRiderCode());
        }
    }

    private static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void setBusy(boolean value) {
        this.busy = value;
        this.updateButtons();
    }

    private Window window() {
        return this.ordersRoot.getScene() == null ? null : this.ordersRoot.getScene().getWindow();
    }
}
