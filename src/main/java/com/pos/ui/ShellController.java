package com.pos.ui;

import com.pos.app.Navigator;
import com.pos.model.User;
import com.pos.service.CartService;
import com.pos.service.Session;
import java.io.IOException;
import java.net.URL;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Window;

public class ShellController {
    private static final Color MUTED = Color.web("#9AA3AE");
    @FXML
    private BorderPane shellRoot;
    @FXML
    private StackPane contentArea;
    @FXML
    private ToggleButton saleNav;
    @FXML
    private ToggleButton ordersNav;
    @FXML
    private ToggleButton productsNav;
    @FXML
    private ToggleButton historyNav;
    @FXML
    private ToggleButton settingsNav;
    @FXML
    private Button logoutButton;
    private final ToggleGroup navGroup = new ToggleGroup();

    @FXML
    private void initialize() {
        this.setUpNavItem(this.saleNav, Icons.Icon.SALE);
        this.setUpNavItem(this.ordersNav, Icons.Icon.PHONE);
        this.setUpNavItem(this.productsNav, Icons.Icon.PRODUCTS);
        this.setUpNavItem(this.historyNav, Icons.Icon.HISTORY);
        this.setUpNavItem(this.settingsNav, Icons.Icon.SETTINGS);
        this.logoutButton.setGraphic(Icons.graphic(Icons.Icon.LOGOUT, MUTED));
        User user = Session.getInstance().getCurrentUser();
        boolean admin = user != null && user.isAdmin();
        showIf(this.productsNav, admin);
        showIf(this.settingsNav, admin);
        this.navGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
            } else {
                this.showScreen(newToggle);
            }
        });
        this.saleNav.setSelected(true);
    }

    private void setUpNavItem(ToggleButton item, Icons.Icon icon) {
        item.setGraphic(Icons.graphic(icon, MUTED));
        item.setToggleGroup(this.navGroup);
    }

    private static void showIf(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void showScreen(Toggle toggle) {
        Node screen;
        if (toggle == this.saleNav) {
            screen = this.load("/fxml/sale.fxml");
        } else if (toggle == this.ordersNav) {
            screen = this.load("/fxml/orders.fxml");
        } else if (toggle == this.productsNav) {
            screen = this.load("/fxml/products.fxml");
        } else if (toggle == this.historyNav) {
            screen = this.load("/fxml/history.fxml");
        } else {
            screen = this.load("/fxml/settings.fxml");
        }

        if (screen != null) {
            this.contentArea.getChildren().setAll(new Node[]{screen});
        }
    }

    private Node load(String fxml) {
        try {
            URL location = ShellController.class.getResource(fxml);
            if (location == null) {
                throw new IOException(fxml + " not found on classpath");
            } else {
                return (Node)FXMLLoader.load(location);
            }
        } catch (RuntimeException | IOException var3) {
            Alerts.error(this.window(), "Could not open screen " + fxml, var3);
            return null;
        }
    }

    @FXML
    private void handleLogout() {
        CartService cart = CartService.getInstance();
        String message = cart.isEmpty() ? "You will return to the sign-in screen." : "The current cart (" + cart.getItemCount() + " items) will be cleared.";
        if (Alerts.confirm(this.window(), "Log out?", message)) {
            cart.clear();
            Session.getInstance().clear();
            Navigator.showLogin();
        }
    }

    private Window window() {
        return this.shellRoot.getScene() == null ? null : this.shellRoot.getScene().getWindow();
    }
}
