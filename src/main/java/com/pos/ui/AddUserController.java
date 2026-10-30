package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.DbErrors;
import com.pos.db.UserDao;
import com.pos.model.Role;
import com.pos.model.User;
import com.pos.service.UserService;
import com.pos.service.ValidationException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

public class AddUserController {
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final Color MUTED = Color.web("#9AA3AE");
    @FXML
    private Button closeButton;
    @FXML
    private TextField usernameField;
    @FXML
    private ComboBox<Role> roleBox;
    @FXML
    private TextField fullNameField;
    @FXML
    private PasswordField pinField;
    @FXML
    private PasswordField pinConfirmField;
    @FXML
    private TextField pinTextField;
    @FXML
    private TextField pinConfirmTextField;
    @FXML
    private Button showPinButton;
    @FXML
    private Button showPinConfirmButton;
    @FXML
    private Label errorLabel;
    @FXML
    private Button cancelButton;
    @FXML
    private Button addButton;
    private final UserDao userDao = new UserDao();
    private Map<String, Control> fields;
    private Map<String, PinReveal> pins;
    private Stage stage;
    private boolean busy;
    private User created;

    public static Optional<User> show(Window owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(AddUserController.class.getResource("/fxml/add-user.fxml"));
        Parent card = (Parent)loader.load();
        AddUserController controller = (AddUserController)loader.getController();
        controller.attach(Overlay.create(owner, card));
        controller.stage.showAndWait();
        return Optional.ofNullable(controller.created);
    }

    @FXML
    private void initialize() {
        this.closeButton.setGraphic(Icons.graphic(Icons.Icon.CLOSE, MUTED));
        this.roleBox.getItems().setAll(new Role[]{Role.CASHIER, Role.ADMIN});
        this.roleBox.setValue(Role.CASHIER);
        this.roleBox.setButtonCell(new AddUserController.RoleCell());
        this.roleBox.setCellFactory(list -> new AddUserController.RoleCell());

        this.pins = Map.of(
            "pin", new PinReveal(this.pinField, this.pinTextField, this.showPinButton),
            "pin_confirm", new PinReveal(this.pinConfirmField, this.pinConfirmTextField, this.showPinConfirmButton)
        );

        for (PinReveal pin : this.pins.values()) {
            pin.onFocused(() -> pin.pseudoClassStateChanged(ERROR, false));
        }

        this.fields = Map.of("username", this.usernameField, "role", this.roleBox, "full_name", this.fullNameField);

        for (Control control : this.fields.values()) {
            control.focusedProperty().addListener((obs, was, focused) -> {
                if (focused) {
                    control.pseudoClassStateChanged(ERROR, false);
                }
            });
        }

        this.errorLabel.managedProperty().bind(this.errorLabel.visibleProperty());
        this.errorLabel.setVisible(false);
        this.errorLabel.pseudoClassStateChanged(ERROR, true);
    }

    private void attach(Stage dialogStage) {
        this.stage = dialogStage;
        this.stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER:
                    e.consume();
                    this.handleAdd();
                    break;
                case ESCAPE:
                    e.consume();
                    this.handleCancel();
            }
        });
        this.stage.setOnShown(e -> this.usernameField.requestFocus());
        this.stage.setOnCloseRequest(e -> {
            if (this.busy) {
                e.consume();
            }
        });
    }

    @FXML
    private void handleCancel() {
        if (!this.busy) {
            this.stage.close();
        }
    }

    @FXML
    private void handleAdd() {
        if (!this.busy) {
            final String pin = this.pinField.getText();

            final User user;
            try {
                user = UserService.validateNewUser(
                    this.usernameField.getText(), this.fullNameField.getText(), (Role)this.roleBox.getValue(), pin, this.pinConfirmField.getText()
                );
            } catch (ValidationException var4) {
                this.showError(var4.getField(), var4.getMessage());
                return;
            }

            this.setBusy(true);
            Task<Integer> task = new Task<Integer>() {
                protected Integer call() throws Exception {
                    return AddUserController.this.userDao.insert(user, pin);
                }
            };
            task.setOnSucceeded(e -> {
                user.setId((Integer)task.getValue());
                this.created = user;
                this.busy = false;
                this.stage.close();
            });
            task.setOnFailed(e -> {
                this.setBusy(false);
                if (DbErrors.isDuplicateKey(task.getException())) {
                    this.showError("username", "This username is already taken");
                } else {
                    Alerts.error(this.stage, "The cashier was not added", task.getException());
                }
            });
            Background.run(task);
        }
    }

    private void showError(String field, String message) {
        Control control = this.fields.get(field);
        PinReveal pin = this.pins.get(field);
        if (control != null) {
            control.requestFocus();
            control.pseudoClassStateChanged(ERROR, true);
        } else if (pin != null) {
            pin.requestFocus();
            pin.pseudoClassStateChanged(ERROR, true);
        }

        this.errorLabel.setText(message);
        this.errorLabel.setVisible(true);
    }

    private void setBusy(boolean value) {
        this.busy = value;
        this.addButton.setDisable(value);
        this.cancelButton.setDisable(value);
        this.closeButton.setDisable(value);
        this.addButton.setText(value ? "Adding..." : "Add Cashier");
    }

    private static class RoleCell extends ListCell<Role> {
        protected void updateItem(Role role, boolean empty) {
            super.updateItem(role, empty);
            this.setText(!empty && role != null ? (role == Role.ADMIN ? "Admin" : "Cashier") : null);
        }
    }
}
