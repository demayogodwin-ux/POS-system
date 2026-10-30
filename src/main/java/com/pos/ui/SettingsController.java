package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.SettingsDao;
import com.pos.db.UserDao;
import com.pos.model.Role;
import com.pos.model.User;
import com.pos.service.CartService;
import com.pos.service.Session;
import com.pos.service.SettingsService;
import com.pos.service.ValidationException;
import com.pos.web.MobileServer;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.animation.PauseTransition;
import javafx.beans.Observable;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import javafx.util.Duration;

public class SettingsController {
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");
    private static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");
    private static final Color MUTED = Color.web("#9AA3AE");
    private static final Color TEXT = Color.web("#ECEEF1");
    @FXML
    private BorderPane settingsRoot;
    @FXML
    private Label messageLabel;
    @FXML
    private Button discardButton;
    @FXML
    private Button saveButton;
    @FXML
    private TextField storeNameField;
    @FXML
    private TextField addressField;
    @FXML
    private TextField footerField;
    @FXML
    private TextField taxRateField;
    @FXML
    private TextField currencyField;
    @FXML
    private TextField mapsKeyField;
    @FXML
    private TextField appAddressField;
    @FXML
    private ToggleButton includeTaxSwitch;
    @FXML
    private ToggleButton printReceiptSwitch;
    @FXML
    private ToggleButton cashDrawerSwitch;
    @FXML
    private ToggleButton beepSwitch;
    @FXML
    private Button addCashierButton;
    @FXML
    private VBox usersBox;
    private final SettingsDao settingsDao = new SettingsDao();
    private final UserDao userDao = new UserDao();
    private final PauseTransition messageTimer = new PauseTransition(Duration.seconds(3.0));
    private Map<String, String> savedForm = Map.of();
    private boolean loaded;
    private boolean busy;

    @FXML
    private void initialize() {
        this.currencyField.setText("System default (" + NumberFormat.getCurrencyInstance().getCurrency().getSymbol() + ")");
        this.addCashierButton.setGraphic(Icons.graphic(Icons.Icon.PLUS, TEXT));
        this.appAddressField.setText(MobileServer.isRunning() ? String.join("   ", MobileServer.addresses()) : "Not running (check the database connection)");
        this.messageLabel.setVisible(false);
        this.messageTimer.setOnFinished(e -> this.messageLabel.setVisible(false));
        Observable[] inputs = new Observable[]{
            this.storeNameField.textProperty(),
            this.addressField.textProperty(),
            this.footerField.textProperty(),
            this.taxRateField.textProperty(),
            this.mapsKeyField.textProperty(),
            this.includeTaxSwitch.selectedProperty(),
            this.printReceiptSwitch.selectedProperty(),
            this.cashDrawerSwitch.selectedProperty(),
            this.beepSwitch.selectedProperty()
        };

        for (Observable input : inputs) {
            input.addListener(o -> this.updateDirty());
        }

        for (TextField field : List.of(this.storeNameField, this.addressField, this.footerField, this.taxRateField)) {
            field.focusedProperty().addListener((obs, was, focused) -> {
                if (focused) {
                    field.pseudoClassStateChanged(ERROR, false);
                }
            });
        }

        this.updateDirty();
        this.load();
    }

    private void load() {
        Task<SettingsController.SettingsData> task = new Task<SettingsController.SettingsData>() {
            protected SettingsController.SettingsData call() throws Exception {
                return new SettingsController.SettingsData(SettingsController.this.settingsDao.getAll(), SettingsController.this.userDao.findAll());
            }
        };
        task.setOnSucceeded(e -> {
            SettingsController.SettingsData data = (SettingsController.SettingsData)task.getValue();
            this.fillForm(data.settings());
            this.showUsers(data.users());
            this.loaded = true;
            this.updateDirty();
        });
        task.setOnFailed(e -> Alerts.error(this.window(), "Could not load settings", task.getException()));
        Background.run(task);
    }

    private void fillForm(Map<String, String> settings) {
        this.storeNameField.setText(settings.getOrDefault("store_name", ""));
        this.addressField.setText(settings.getOrDefault("store_address", ""));
        this.footerField.setText(settings.getOrDefault("receipt_footer", ""));
        this.taxRateField.setText(SettingsService.taxPercentForDisplay(settings.get("tax_rate")));
        this.mapsKeyField.setText(settings.getOrDefault("google_maps_api_key", ""));
        this.includeTaxSwitch.setSelected(flag(settings, "prices_include_tax"));
        this.printReceiptSwitch.setSelected(flag(settings, "print_receipt"));
        this.cashDrawerSwitch.setSelected(flag(settings, "open_cash_drawer"));
        this.beepSwitch.setSelected(flag(settings, "beep_on_scan"));
        this.savedForm = this.formSnapshot();
    }

    private static boolean flag(Map<String, String> settings, String key) {
        return CartService.parseFlag(settings.get(key));
    }

    private Map<String, String> formSnapshot() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("store_name", this.storeNameField.getText());
        form.put("store_address", this.addressField.getText());
        form.put("receipt_footer", this.footerField.getText());
        form.put("tax_rate", this.taxRateField.getText());
        form.put("google_maps_api_key", this.mapsKeyField.getText());
        form.put("prices_include_tax", String.valueOf(this.includeTaxSwitch.isSelected()));
        form.put("print_receipt", String.valueOf(this.printReceiptSwitch.isSelected()));
        form.put("open_cash_drawer", String.valueOf(this.cashDrawerSwitch.isSelected()));
        form.put("beep_on_scan", String.valueOf(this.beepSwitch.isSelected()));
        return form;
    }

    private void updateDirty() {
        boolean dirty = this.loaded && !this.formSnapshot().equals(this.savedForm);
        this.saveButton.setDisable(!dirty || this.busy);
        this.discardButton.setDisable(!dirty || this.busy);
    }

    @FXML
    private void handleDiscard() {
        Map<String, String> form = this.savedForm;
        this.storeNameField.setText(form.get("store_name"));
        this.addressField.setText(form.get("store_address"));
        this.footerField.setText(form.get("receipt_footer"));
        this.taxRateField.setText(form.get("tax_rate"));
        this.mapsKeyField.setText(form.get("google_maps_api_key"));
        this.includeTaxSwitch.setSelected(Boolean.parseBoolean(form.get("prices_include_tax")));
        this.printReceiptSwitch.setSelected(Boolean.parseBoolean(form.get("print_receipt")));
        this.cashDrawerSwitch.setSelected(Boolean.parseBoolean(form.get("open_cash_drawer")));
        this.beepSwitch.setSelected(Boolean.parseBoolean(form.get("beep_on_scan")));

        for (TextField field : List.of(this.storeNameField, this.addressField, this.footerField, this.taxRateField)) {
            field.pseudoClassStateChanged(ERROR, false);
        }

        this.messageLabel.setVisible(false);
    }

    @FXML
    private void handleSave() {
        if (!this.busy) {
            final Map<String, String> toStore = new LinkedHashMap<>();

            try {
                toStore.put("store_name", SettingsService.requireStoreName(this.storeNameField.getText()));
                toStore.put("store_address", SettingsService.limitText("store_address", "Address", this.addressField.getText()));
                toStore.put("receipt_footer", SettingsService.limitText("receipt_footer", "Receipt footer", this.footerField.getText()));
                toStore.put("tax_rate", SettingsService.taxRateToStore(this.taxRateField.getText()));
                toStore.put("google_maps_api_key", SettingsService.mapsKeyToStore(this.mapsKeyField.getText()));
            } catch (ValidationException var3) {
                this.markInvalid(var3.getField());
                this.showMessage(var3.getMessage(), true);
                return;
            }

            toStore.put("prices_include_tax", String.valueOf(this.includeTaxSwitch.isSelected()));
            toStore.put("print_receipt", String.valueOf(this.printReceiptSwitch.isSelected()));
            toStore.put("open_cash_drawer", String.valueOf(this.cashDrawerSwitch.isSelected()));
            toStore.put("beep_on_scan", String.valueOf(this.beepSwitch.isSelected()));
            this.setBusy(true);
            Task<Void> task = new Task<Void>() {
                protected Void call() throws Exception {
                    SettingsController.this.settingsDao.saveAll(toStore);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                CartService.getInstance().applyTaxSettings(toStore.get("tax_rate"), toStore.get("prices_include_tax"));
                this.fillForm(toStore);
                this.setBusy(false);
                this.showMessage("Settings saved", false);
            });
            task.setOnFailed(e -> {
                this.setBusy(false);
                Alerts.error(this.window(), "Settings were not saved", task.getException());
            });
            Background.run(task);
        }
    }

    private void markInvalid(String field) {
        TextField control = switch (field) {
            case "store_name" -> this.storeNameField;
            case "store_address" -> this.addressField;
            case "receipt_footer" -> this.footerField;
            case "tax_rate" -> this.taxRateField;
            case "google_maps_api_key" -> this.mapsKeyField;
            default -> null;
        };
        if (control != null) {
            control.requestFocus();
            control.pseudoClassStateChanged(ERROR, true);
        }
    }

    private void setBusy(boolean value) {
        this.busy = value;
        this.saveButton.setText(value ? "Saving..." : "Save Settings");
        this.updateDirty();
    }

    private void showMessage(String message, boolean isError) {
        this.messageLabel.setText(message);
        this.messageLabel.pseudoClassStateChanged(ERROR, isError);
        this.messageLabel.pseudoClassStateChanged(SUCCESS, !isError);
        this.messageLabel.setVisible(true);
        if (isError) {
            this.messageTimer.stop();
        } else {
            this.messageTimer.playFromStart();
        }
    }

    private void reloadUsers() {
        Task<List<User>> task = new Task<List<User>>() {
            protected List<User> call() throws Exception {
                return SettingsController.this.userDao.findAll();
            }
        };
        task.setOnSucceeded(e -> this.showUsers((List<User>)task.getValue()));
        task.setOnFailed(e -> Alerts.error(this.window(), "Could not load cashiers", task.getException()));
        Background.run(task);
    }

    private void showUsers(List<User> users) {
        this.usersBox.getChildren().clear();

        for (User user : users) {
            this.usersBox.getChildren().add(this.userRow(user));
        }
    }

    private Node userRow(User user) {
        StackPane avatar = new StackPane(new Node[]{Icons.graphic(Icons.Icon.USER, MUTED)});
        avatar.getStyleClass().add("user-avatar");
        Label name = new Label(user.getFullName());
        name.getStyleClass().add("setting-name");
        Label username = new Label("@" + user.getUsername() + (user.isActive() ? "" : " - inactive"));
        username.getStyleClass().addAll(new String[]{"muted", "setting-hint"});
        VBox names = new VBox(2.0, new Node[]{name, username});
        names.setMinWidth(0.0);
        HBox.setHgrow(names, Priority.ALWAYS);
        Label role = new Label(user.getRole() == Role.ADMIN ? "Admin" : "Cashier");
        role.getStyleClass().add(user.getRole() == Role.ADMIN ? "chip-warning" : "chip-neutral");
        ToggleButton active = new ToggleButton();
        active.getStyleClass().add("switch");
        active.setSelected(user.isActive());
        HBox row = new HBox(12.0, new Node[]{avatar, names, role, active});
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("user-row");
        row.pseudoClassStateChanged(INACTIVE, !user.isActive());
        User me = Session.getInstance().getCurrentUser();
        if (me != null && me.getId() == user.getId()) {
            active.setDisable(true);
            Tooltip.install(row, new Tooltip("You cannot deactivate your own account"));
        } else {
            active.setOnAction(e -> this.changeActive(user, active, row, username));
        }

        return row;
    }

    private void changeActive(final User user, ToggleButton toggle, HBox row, Label username) {
        final boolean newValue = toggle.isSelected();
        toggle.setDisable(true);
        Task<Void> task = new Task<Void>() {
            protected Void call() throws Exception {
                SettingsController.this.userDao.setActive(user.getId(), newValue);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            toggle.setDisable(false);
            user.setActive(newValue);
            row.pseudoClassStateChanged(INACTIVE, !newValue);
            username.setText("@" + user.getUsername() + (newValue ? "" : " - inactive"));
        });
        task.setOnFailed(e -> {
            toggle.setDisable(false);
            toggle.setSelected(!newValue);
            Alerts.error(this.window(), "Could not change " + user.getFullName(), task.getException());
        });
        Background.run(task);
    }

    @FXML
    private void handleAddCashier() {
        Optional<User> created;
        try {
            created = AddUserController.show(this.window());
        } catch (RuntimeException | IOException var3) {
            Alerts.error(this.window(), "Could not open Add Cashier", var3);
            return;
        }

        created.ifPresent(user -> {
            this.reloadUsers();
            this.showMessage(user.getFullName() + " added", false);
        });
    }

    private Window window() {
        return this.settingsRoot.getScene() == null ? null : this.settingsRoot.getScene().getWindow();
    }

    private record SettingsData(Map<String, String> settings, List<User> users) {
    }
}
