package com.pos.ui;

import com.pos.app.Background;
import com.pos.app.Navigator;
import com.pos.db.SettingsDao;
import com.pos.db.UserDao;
import com.pos.model.User;
import com.pos.service.Session;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.Duration;

public class LoginController {
    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());
    private static final String DEFAULT_STORE_NAME = "My Store POS";
    private static final int MAX_PIN_LENGTH = 6;
    private static final int MAX_FAILURES = 5;
    private static final int LOCKOUT_SECONDS = 30;
    @FXML
    private Label storeNameLabel;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField pinField;
    @FXML
    private TextField pinTextField;
    @FXML
    private Button showPinButton;
    @FXML
    private Label errorLabel;
    @FXML
    private Button signInButton;
    private final UserDao userDao = new UserDao();
    private final SettingsDao settingsDao = new SettingsDao();
    private int failures;
    private boolean busy;
    private Timeline lockout;
    private PinReveal pinReveal;

    @FXML
    private void initialize() {
        this.pinReveal = new PinReveal(this.pinField, this.pinTextField, this.showPinButton);
        this.errorLabel.managedProperty().bind(this.errorLabel.visibleProperty());
        this.usernameField.textProperty().addListener((obs, oldText, newText) -> this.hideErrorUnlessLocked());
        this.loadStoreName();
        Platform.runLater(this.usernameField::requestFocus);
    }

    private void loadStoreName() {
        Task<String> task = new Task<String>() {
            protected String call() throws Exception {
                return LoginController.this.settingsDao.get("store_name", "My Store POS");
            }
        };
        task.setOnSucceeded(e -> this.storeNameLabel.setText((String)task.getValue()));
        task.setOnFailed(e -> LOG.log(Level.WARNING, "Could not load store_name", task.getException()));
        Background.run(task);
    }

    @FXML
    private void handleSignIn() {
        if (!this.busy && this.lockout == null) {
            final String username = this.usernameField.getText().trim();
            final String pin = this.pinField.getText();
            if (!username.isEmpty() && !pin.isEmpty()) {
                this.hideError();
                this.setBusy(true);
                Task<Optional<User>> task = new Task<Optional<User>>() {
                    protected Optional<User> call() throws Exception {
                        return LoginController.this.userDao.authenticate(username, pin);
                    }
                };
                task.setOnSucceeded(e -> {
                    this.setBusy(false);
                    task.getValue().ifPresentOrElse(this::signedIn, this::wrongCredentials);
                });
                task.setOnFailed(
                    e -> {
                        this.setBusy(false);
                        Alerts.error(
                            this.signInButton.getScene().getWindow(),
                            "Could not sign in. Check the internet connection and the .env file.",
                            task.getException()
                        );
                    }
                );
                Background.run(task);
            } else {
                this.showError("Enter your username and PIN");
                (username.isEmpty() ? this.usernameField : this.pinReveal.active()).requestFocus();
            }
        }
    }

    private void signedIn(User user) {
        Session.getInstance().start(user);
        LOG.info("Signed in: " + user);
        Navigator.showShell();
    }

    private void wrongCredentials() {
        this.failures++;
        this.pinField.clear();
        if (this.failures >= 5) {
            this.startLockout();
        } else {
            this.showError("Wrong username or PIN");
            this.pinReveal.requestFocus();
        }
    }

    private void startLockout() {
        this.signInButton.setDisable(true);
        int[] remaining = new int[]{30};
        this.showError(lockoutMessage(remaining[0]));
        this.lockout = new Timeline(new KeyFrame[]{new KeyFrame(Duration.seconds(1.0), e -> {
            remaining[0]--;
            this.showError(lockoutMessage(remaining[0]));
        }, new KeyValue[0])});
        this.lockout.setCycleCount(30);
        this.lockout.setOnFinished(e -> {
            this.lockout = null;
            this.failures = 0;
            this.signInButton.setDisable(false);
            this.hideError();
            this.pinReveal.requestFocus();
        });
        this.lockout.play();
    }

    private static String lockoutMessage(int seconds) {
        return "Too many attempts. Try again in " + seconds + "s";
    }

    private void setBusy(boolean value) {
        this.busy = value;
        this.signInButton.setDisable(value || this.lockout != null);
        this.signInButton.setText(value ? "Signing in..." : "Sign in");
        this.usernameField.setDisable(value);
        this.pinField.setDisable(value);
    }

    private void showError(String message) {
        this.errorLabel.setText(message);
        this.errorLabel.setVisible(true);
    }

    private void hideError() {
        this.errorLabel.setVisible(false);
    }

    private void hideErrorUnlessLocked() {
        if (this.lockout == null) {
            this.hideError();
        }
    }
}
