package com.pos.ui;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Window;

public final class Alerts {
    private static final Logger LOG = Logger.getLogger(Alerts.class.getName());

    private Alerts() {
    }

    public static void error(Window owner, String header, Throwable error) {
        LOG.log(Level.SEVERE, header, error);
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) {
            message = error == null ? "Unknown error" : error.getClass().getSimpleName();
        }

        create(AlertType.ERROR, owner, header, message).showAndWait();
    }

    public static void info(Window owner, String header, String content) {
        create(AlertType.INFORMATION, owner, header, content).showAndWait();
    }

    public static boolean confirm(Window owner, String header, String content) {
        return create(AlertType.CONFIRMATION, owner, header, content).showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    public static void style(Dialog<?> dialog) {
        URL css = Alerts.class.getResource("/css/dark-pro.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
    }

    private static Alert create(AlertType type, Window owner, String header, String content) {
        Alert alert = new Alert(type);
        if (owner != null) {
            alert.initOwner(owner);
        }

        alert.setTitle("My Store POS");
        alert.setHeaderText(header);
        alert.setContentText(content);
        style(alert);
        return alert;
    }
}
