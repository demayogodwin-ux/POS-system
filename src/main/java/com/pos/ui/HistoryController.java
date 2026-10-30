package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.SaleDao;
import com.pos.db.SettingsDao;
import com.pos.model.DaySummary;
import com.pos.model.PaymentMethod;
import com.pos.model.Sale;
import com.pos.model.SaleItem;
import com.pos.model.User;
import com.pos.service.CsvExporter;
import com.pos.service.Money;
import com.pos.service.Session;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.StringConverter;

public class HistoryController {
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private static final Color TEXT = Color.web("#ECEEF1");
    private static final Color DANGER = Color.web("#F87171");
    @FXML
    private BorderPane historyRoot;
    @FXML
    private Label dateLabel;
    @FXML
    private DatePicker datePicker;
    @FXML
    private ToggleButton allDaysToggle;
    @FXML
    private Button exportButton;
    @FXML
    private Label salesCaption;
    @FXML
    private Label totalSalesLabel;
    @FXML
    private Label countLabel;
    @FXML
    private Label averageLabel;
    @FXML
    private Label itemsSoldLabel;
    @FXML
    private Label taxLabel;
    @FXML
    private TableView<Sale> salesTable;
    @FXML
    private TableColumn<Sale, String> receiptColumn;
    @FXML
    private TableColumn<Sale, LocalDateTime> timeColumn;
    @FXML
    private TableColumn<Sale, String> cashierColumn;
    @FXML
    private TableColumn<Sale, String> paymentColumn;
    @FXML
    private TableColumn<Sale, Integer> itemsColumn;
    @FXML
    private TableColumn<Sale, BigDecimal> totalColumn;
    @FXML
    private TableColumn<Sale, String> statusColumn;
    @FXML
    private Label emptyDetailLabel;
    @FXML
    private VBox detailBox;
    @FXML
    private Label detailTitleLabel;
    @FXML
    private Label detailStatusChip;
    @FXML
    private Label detailMetaLabel;
    @FXML
    private VBox itemsBox;
    @FXML
    private Label detailSubtotalLabel;
    @FXML
    private Label detailTaxLabel;
    @FXML
    private Label detailTotalLabel;
    @FXML
    private Label detailPaidCaption;
    @FXML
    private Label detailPaidLabel;
    @FXML
    private HBox detailChangeRow;
    @FXML
    private Label detailChangeLabel;
    @FXML
    private Button reprintButton;
    @FXML
    private Button voidButton;
    private final SaleDao saleDao = new SaleDao();
    private final SettingsDao settingsDao = new SettingsDao();
    private final ObservableList<Sale> sales = FXCollections.observableArrayList();
    private Map<String, String> settings = Map.of();
    private boolean admin;
    private Sale shownSale;

    @FXML
    private void initialize() {
        User user = Session.getInstance().getCurrentUser();
        this.admin = user != null && user.isAdmin();
        this.datePicker.setConverter(new StringConverter<LocalDate>() {
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(HistoryController.SHORT_DATE);
            }

            public LocalDate fromString(String text) {
                return text != null && !text.isBlank() ? LocalDate.parse(text, HistoryController.SHORT_DATE) : null;
            }
        });
        this.datePicker.setValue(LocalDate.now());
        this.datePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate == null) {
                this.datePicker.setValue(oldDate);
            } else {
                this.loadDay(null);
            }
        });
        this.datePicker.disableProperty().bind(this.allDaysToggle.selectedProperty());
        this.allDaysToggle.selectedProperty().addListener((obs, was, all) -> {
            // Sales from different days need the date next to the time.
            fixWidth(this.timeColumn, all ? 175.0 : 110.0);
            this.salesTable.refresh();
            this.loadDay(null);
        });
        this.reprintButton.setGraphic(Icons.graphic(Icons.Icon.PRINTER, TEXT));
        this.voidButton.setGraphic(Icons.graphic(Icons.Icon.CLOSE, DANGER));
        this.detailBox.managedProperty().bind(this.detailBox.visibleProperty());
        this.emptyDetailLabel.visibleProperty().bind(this.detailBox.visibleProperty().not());
        this.setUpTable();
        this.loadDay(null);
    }

    private void loadDay(Integer selectId) {
        // A null date means every day (the "All days" button).
        final LocalDate date = this.allDaysToggle.isSelected() ? null : (LocalDate)this.datePicker.getValue();
        boolean today = date != null && date.equals(LocalDate.now());
        this.dateLabel.setText(date == null ? "All days" : date.format(LONG_DATE));
        this.salesCaption.setText(date == null ? "ALL SALES" : today ? "TODAY'S SALES" : "SALES");
        this.salesTable.setPlaceholder(new Label("Loading sales..."));
        Task<HistoryController.DayData> task = new Task<HistoryController.DayData>() {
            protected HistoryController.DayData call() throws Exception {
                return new HistoryController.DayData(
                    HistoryController.this.saleDao.findByDate(date),
                    HistoryController.this.saleDao.daySummary(date),
                    HistoryController.this.settingsDao.getAll()
                );
            }
        };
        task.setOnSucceeded(e -> {
            HistoryController.DayData data = (HistoryController.DayData)task.getValue();
            this.settings = data.settings();
            this.sales.setAll(data.sales());
            this.showSummary(data.summary());
            this.salesTable.setPlaceholder(new Label(date == null ? "No sales yet" : "No sales on " + date.format(SHORT_DATE)));
            this.exportButton.setDisable(this.sales.isEmpty());
            Sale reselect = selectId == null ? null : this.sales.stream().filter(s -> s.getId() == selectId).findFirst().orElse(null);
            if (reselect != null) {
                this.salesTable.getSelectionModel().select(reselect);
                this.salesTable.scrollTo(reselect);
            } else {
                this.showDetail(null);
            }
        });
        task.setOnFailed(e -> {
            this.salesTable.setPlaceholder(new Label("Sales could not be loaded"));
            Alerts.error(this.window(), "Could not load sales history", task.getException());
        });
        Background.run(task);
    }

    private void showSummary(DaySummary summary) {
        this.totalSalesLabel.setText(Money.currency(summary.totalSales()));
        int count = summary.saleCount();
        this.countLabel.setText(count + (count == 1 ? " paid sale" : " paid sales"));
        this.averageLabel.setText(Money.currency(summary.averageSale()));
        this.itemsSoldLabel.setText(String.format(Locale.US, "%,d", summary.itemsSold()));
        this.taxLabel.setText(Money.currency(summary.taxCollected()));
    }

    private void setUpTable() {
        this.receiptColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(((Sale)cd.getValue()).getReceiptNo()));
        this.timeColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(((Sale)cd.getValue()).getSaleTime()));
        this.timeColumn.setCellFactory(col -> textCell(time -> time.format(this.allDaysToggle.isSelected() ? DATE_TIME : TIME)));
        this.cashierColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(((Sale)cd.getValue()).getCashierName()));
        this.paymentColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(((Sale)cd.getValue()).getPaymentMethod().label()));
        this.itemsColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper(((Sale)cd.getValue()).itemCount()));
        this.totalColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper(((Sale)cd.getValue()).getTotal()));
        this.totalColumn.setCellFactory(col -> textCell(Money::currency));
        this.statusColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(((Sale)cd.getValue()).getStatus()));
        this.statusColumn.setCellFactory(col -> new TableCell<Sale, String>() {
            private final Label chip = new Label();

            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                this.setText(null);
                if (!empty && status != null) {
                    HistoryController.applyStatusChip(this.chip, status);
                    this.setGraphic(this.chip);
                } else {
                    this.setGraphic(null);
                }
            }
        });
        fixWidth(this.receiptColumn, 130.0);
        fixWidth(this.timeColumn, 110.0);
        fixWidth(this.paymentColumn, 110.0);
        fixWidth(this.itemsColumn, 80.0);
        fixWidth(this.totalColumn, 140.0);
        fixWidth(this.statusColumn, 120.0);
        this.salesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        this.salesTable.setItems(this.sales);
        this.salesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSale, sale) -> this.showDetail(sale));
    }

    private static void fixWidth(TableColumn<?, ?> column, double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
    }

    private static <T> TableCell<Sale, T> textCell(final Function<T, String> format) {
        return new TableCell<Sale, T>() {
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                this.setText(!empty && item != null ? format.apply(item) : null);
            }
        };
    }

    private static void applyStatusChip(Label chip, String status) {
        switch (status) {
            case "PAID":
                setChip(chip, "Paid", "chip-success");
                break;
            case "VOID":
                setChip(chip, "Void", "chip-neutral");
                break;
            case "REFUNDED":
                setChip(chip, "Refunded", "chip-warning");
                break;
            default:
                setChip(chip, status, "chip-neutral");
        }
    }

    private static void setChip(Label chip, String text, String styleClass) {
        chip.setText(text);
        chip.getStyleClass().setAll(new String[]{"label", styleClass});
    }

    private void showDetail(final Sale sale) {
        this.shownSale = sale;
        if (sale == null) {
            this.detailBox.setVisible(false);
        } else {
            this.detailBox.setVisible(true);
            this.detailTitleLabel.setText("Receipt " + sale.getReceiptNo());
            applyStatusChip(this.detailStatusChip, sale.getStatus());
            this.detailMetaLabel.setText(sale.getSaleTime().format(SHORT_DATE) + " - " + sale.getSaleTime().format(TIME) + " - " + sale.getCashierName());
            this.detailSubtotalLabel.setText(Money.currency(sale.getSubtotal()));
            this.detailTaxLabel.setText(Money.currency(sale.getTax()));
            this.detailTotalLabel.setText(Money.currency(sale.getTotal()));
            this.detailPaidCaption.setText(sale.getPaymentMethod().label());
            this.detailPaidLabel.setText(Money.currency(sale.getAmountPaid()));
            boolean cash = sale.getPaymentMethod() == PaymentMethod.CASH;
            this.detailChangeRow.setVisible(cash);
            this.detailChangeRow.setManaged(cash);
            this.detailChangeLabel.setText(Money.currency(sale.getChangeDue()));
            boolean canVoid = this.admin && sale.isPaid();
            this.voidButton.setVisible(canVoid);
            this.voidButton.setManaged(canVoid);
            this.voidButton.setDisable(false);
            this.reprintButton.setDisable(true);
            this.itemsBox.getChildren().setAll(new Node[]{new Label("Loading items...")});
            if (!sale.getItems().isEmpty()) {
                this.showItems(sale);
            } else {
                Task<List<SaleItem>> task = new Task<List<SaleItem>>() {
                    protected List<SaleItem> call() throws Exception {
                        return HistoryController.this.saleDao.findItems(sale.getId());
                    }
                };
                task.setOnSucceeded(e -> {
                    sale.setItems((List<SaleItem>)task.getValue());
                    if (this.shownSale == sale) {
                        this.showItems(sale);
                    }
                });
                task.setOnFailed(e -> {
                    if (this.shownSale == sale) {
                        this.itemsBox.getChildren().setAll(new Node[]{new Label("Items could not be loaded")});
                    }

                    Alerts.error(this.window(), "Could not load the items of " + sale.getReceiptNo(), task.getException());
                });
                Background.run(task);
            }
        }
    }

    private void showItems(Sale sale) {
        this.itemsBox.getChildren().clear();

        for (SaleItem item : sale.getItems()) {
            Label quantity = new Label(item.getQuantity() + "x");
            quantity.getStyleClass().addAll(new String[]{"cart-qty", "mono"});
            Label name = new Label(item.getProductName());
            name.getStyleClass().add("cart-name");
            name.setMinWidth(0.0);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            Label total = new Label(Money.currency(item.getLineTotal()));
            total.getStyleClass().addAll(new String[]{"cart-line-total", "mono"});
            total.setMinWidth(Double.NEGATIVE_INFINITY);
            HBox row = new HBox(12.0, new Node[]{quantity, name, total});
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("cart-row");
            this.itemsBox.getChildren().add(row);
        }

        this.reprintButton.setDisable(false);
    }

    @FXML
    private void handleReprint() {
        if (this.shownSale != null && !this.shownSale.getItems().isEmpty()) {
            ReceiptView receipt = new ReceiptView(this.shownSale, ReceiptView.StoreInfo.fromSettings(this.settings));
            ReceiptPrinter.print(receipt, this.window(), true);
        }
    }

    @FXML
    private void handleVoid() {
        final Sale sale = this.shownSale;
        if (sale != null && this.admin && sale.isPaid()) {
            boolean confirmed = Alerts.confirm(
                this.window(),
                "Void receipt " + sale.getReceiptNo() + "?",
                "The sale will be marked VOID and " + sale.itemCount() + " items will go back into stock. This cannot be undone."
            );
            if (confirmed) {
                this.voidButton.setDisable(true);
                Task<Void> task = new Task<Void>() {
                    protected Void call() throws Exception {
                        HistoryController.this.saleDao.voidSale(sale.getId());
                        return null;
                    }
                };
                task.setOnSucceeded(e -> this.loadDay(sale.getId()));
                task.setOnFailed(e -> {
                    this.voidButton.setDisable(false);
                    Alerts.error(this.window(), "Receipt " + sale.getReceiptNo() + " was not voided", task.getException());
                });
                Background.run(task);
            }
        }
    }

    @FXML
    private void handleExport() {
        if (!this.sales.isEmpty()) {
            LocalDate date = (LocalDate)this.datePicker.getValue();
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export sales to CSV");
            chooser.setInitialFileName(this.allDaysToggle.isSelected() ? "sales-all-days.csv" : "sales-" + date + ".csv");
            chooser.getExtensionFilters().add(new ExtensionFilter("CSV files", new String[]{"*.csv"}));
            final File file = chooser.showSaveDialog(this.window());
            if (file != null) {
                final List<Sale> rows = List.copyOf(this.sales);
                this.exportButton.setDisable(true);
                Task<Void> task = new Task<Void>() {
                    protected Void call() throws Exception {
                        CsvExporter.writeSales(file.toPath(), rows);
                        return null;
                    }
                };
                task.setOnSucceeded(e -> {
                    this.exportButton.setDisable(false);
                    Alerts.info(this.window(), "Export complete", rows.size() + " sales saved to\n" + file.getAbsolutePath());
                });
                task.setOnFailed(e -> {
                    this.exportButton.setDisable(false);
                    Alerts.error(this.window(), "Could not export the CSV file", task.getException());
                });
                Background.run(task);
            }
        }
    }

    private Window window() {
        return this.historyRoot.getScene() == null ? null : this.historyRoot.getScene().getWindow();
    }

    private record DayData(List<Sale> sales, DaySummary summary, Map<String, String> settings) {
    }
}
