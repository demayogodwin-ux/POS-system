package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.InsufficientStockException;
import com.pos.db.SaleDao;
import com.pos.model.PaymentMethod;
import com.pos.model.Sale;
import com.pos.service.CartService;
import com.pos.service.Money;
import com.pos.service.SaleService;
import com.pos.service.Session;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

public class CheckoutController {
    private static final Logger LOG = Logger.getLogger(CheckoutController.class.getName());
    private static final PseudoClass SHORT = PseudoClass.getPseudoClass("short");
    private static final Color MUTED = Color.web("#9AA3AE");
    @FXML
    private Button closeButton;
    @FXML
    private Label amountDueLabel;
    @FXML
    private Label summaryLabel;
    @FXML
    private ToggleButton cashToggle;
    @FXML
    private ToggleButton cardToggle;
    @FXML
    private ToggleButton ewalletToggle;
    @FXML
    private VBox cashSection;
    @FXML
    private TextField cashField;
    @FXML
    private Button exactButton;
    @FXML
    private Button next100Button;
    @FXML
    private Button next500Button;
    @FXML
    private Button next1000Button;
    @FXML
    private HBox changeBox;
    @FXML
    private Label changeCaption;
    @FXML
    private Label changeLabel;
    @FXML
    private Button cancelButton;
    @FXML
    private Button completeButton;
    private final CartService cart = CartService.getInstance();
    private final SaleDao saleDao = new SaleDao();
    private final ToggleGroup methodGroup = new ToggleGroup();
    private final ObjectProperty<BigDecimal> cashReceived = new SimpleObjectProperty();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private Stage stage;
    private BigDecimal total;
    private BooleanBinding valid;
    private Sale completedSale;
    private InsufficientStockException stockError;

    public static CheckoutController show(Window owner, BigDecimal tendered) throws IOException {
        FXMLLoader loader = new FXMLLoader(CheckoutController.class.getResource("/fxml/checkout.fxml"));
        Parent card = (Parent)loader.load();
        CheckoutController controller = (CheckoutController)loader.getController();
        controller.attach(Overlay.create(owner, card), tendered);
        controller.stage.showAndWait();
        return controller;
    }

    public Sale getCompletedSale() {
        return this.completedSale;
    }

    public InsufficientStockException getStockError() {
        return this.stockError;
    }

    @FXML
    private void initialize() {
        this.total = this.cart.getTotal();
        this.closeButton.setGraphic(Icons.graphic(Icons.Icon.CLOSE, MUTED));
        this.amountDueLabel.setText(Money.currency(this.total));
        int count = this.cart.getItemCount();
        this.summaryLabel
            .setText(
                count + (count == 1 ? " item" : " items") + " - includes " + Money.currency(this.cart.getTax()) + " tax (" + this.cart.taxPercentText() + "%)"
            );
        this.setUpMethods();
        this.setUpCash();
        this.valid = Bindings.createBooleanBinding(
            () -> !this.cashToggle.isSelected() || this.cashReceived.get() != null && ((BigDecimal)this.cashReceived.get()).compareTo(this.total) >= 0,
            new Observable[]{this.cashToggle.selectedProperty(), this.cashReceived}
        );
        this.completeButton.disableProperty().bind(this.valid.not().or(this.busy));

        for (Node control : new Node[]{
            this.closeButton,
            this.cancelButton,
            this.cashToggle,
            this.cardToggle,
            this.ewalletToggle,
            this.cashField,
            this.exactButton,
            this.next100Button,
            this.next500Button,
            this.next1000Button
        }) {
            control.disableProperty().bind(this.busy);
        }
    }

    private void setUpMethods() {
        this.cashToggle.setUserData(PaymentMethod.CASH);
        this.cardToggle.setUserData(PaymentMethod.CARD);
        this.ewalletToggle.setUserData(PaymentMethod.EWALLET);

        for (ToggleButton toggle : new ToggleButton[]{this.cashToggle, this.cardToggle, this.ewalletToggle}) {
            toggle.setToggleGroup(this.methodGroup);
        }

        this.cashToggle.setSelected(true);
        this.methodGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
            } else if (newToggle == this.cashToggle) {
                this.cashField.requestFocus();
            }
        });
        this.cashSection.visibleProperty().bind(this.cashToggle.selectedProperty());
        this.cashSection.managedProperty().bind(this.cashToggle.selectedProperty());
        this.changeBox.visibleProperty().bind(this.cashToggle.selectedProperty());
        this.changeBox.managedProperty().bind(this.cashToggle.selectedProperty());
    }

    private void setUpCash() {
        this.cashField.setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().matches("\\d{0,9}(\\.\\d{0,2})?") ? change : null));
        this.cashField.textProperty().addListener((obs, oldText, newText) -> {
            this.cashReceived.set(parseCash(newText));
            this.updateChange();
        });
        BigDecimal hundred = new BigDecimal("100");
        BigDecimal fiveHundred = new BigDecimal("500");
        BigDecimal thousand = new BigDecimal("1000");
        this.quickCash(this.exactButton, this.total, false);
        this.quickCash(this.next100Button, Money.nextStep(this.total, hundred), true);
        this.quickCash(this.next500Button, Money.nextStep(this.total, fiveHundred), true);
        this.quickCash(this.next1000Button, Money.nextStep(this.total, thousand), true);
        this.updateChange();
    }

    private void quickCash(Button button, BigDecimal amount, boolean showAmount) {
        if (showAmount) {
            button.setText(Money.currency(amount));
        }

        button.setOnAction(e -> {
            this.cashField.setText(amount.toPlainString());
            this.cashField.requestFocus();
            this.cashField.end();
        });
    }

    private static BigDecimal parseCash(String text) {
        return text != null && !text.isBlank() && !text.equals(".") ? Money.of(new BigDecimal(text)) : null;
    }

    private void updateChange() {
        BigDecimal cash = (BigDecimal)this.cashReceived.get();
        BigDecimal difference = cash == null ? null : cash.subtract(this.total);
        boolean isShort = difference != null && difference.signum() < 0;
        this.changeBox.pseudoClassStateChanged(SHORT, isShort);
        if (isShort) {
            this.changeCaption.setText("SHORT");
            this.changeLabel.setText("Short by " + Money.currency(difference.negate()));
        } else {
            this.changeCaption.setText("CHANGE");
            this.changeLabel.setText(Money.currency(difference == null ? Money.ZERO : difference));
        }
    }

    private void attach(Stage dialogStage, BigDecimal tendered) {
        this.stage = dialogStage;
        if (tendered != null) {
            this.cashField.setText(tendered.toPlainString());
        }

        this.stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER:
                    e.consume();
                    this.handleComplete();
                    break;
                case ESCAPE:
                    e.consume();
                    this.handleCancel();
            }
        });
        this.stage.setOnShown(e -> {
            this.cashField.requestFocus();
            this.cashField.end();
        });
        this.stage.setOnCloseRequest(e -> {
            if (this.busy.get()) {
                e.consume();
            }
        });
    }

    @FXML
    private void handleCancel() {
        if (!this.busy.get()) {
            this.stage.close();
        }
    }

    @FXML
    private void handleComplete() {
        if (!this.busy.get() && this.valid.get()) {
            PaymentMethod method = (PaymentMethod)this.methodGroup.getSelectedToggle().getUserData();

            final Sale sale;
            try {
                sale = SaleService.fromCart(this.cart, Session.getInstance().getCurrentUser(), method, (BigDecimal)this.cashReceived.get());
            } catch (RuntimeException var4) {
                Alerts.error(this.stage, "The sale could not be prepared", var4);
                return;
            }

            this.setBusy(true);
            Task<Sale> task = new Task<Sale>() {
                protected Sale call() throws Exception {
                    return CheckoutController.this.saleDao.save(sale);
                }
            };
            task.setOnSucceeded(e -> {
                this.completedSale = (Sale)task.getValue();
                this.busy.set(false);
                this.stage.close();
            });
            task.setOnFailed(e -> {
                this.setBusy(false);
                Throwable error = task.getException();
                if (error instanceof InsufficientStockException stock) {
                    LOG.log(Level.WARNING, stock.getMessage(), (Throwable)stock);
                    this.stockError = stock;
                    this.stage.close();
                } else {
                    Alerts.error(this.stage, "The sale was not saved. The cart has been kept.", error);
                }
            });
            Background.run(task);
        }
    }

    private void setBusy(boolean value) {
        this.busy.set(value);
        if (value) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setPrefSize(18.0, 18.0);
            spinner.setMaxSize(18.0, 18.0);
            this.completeButton.setGraphic(spinner);
            this.completeButton.setText("Processing...");
        } else {
            this.completeButton.setGraphic(null);
            this.completeButton.setText("Complete Sale");
        }
    }
}
