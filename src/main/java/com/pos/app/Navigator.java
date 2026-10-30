package com.pos.app;

import com.pos.ui.Alerts;
import java.io.IOException;
import java.net.URL;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public final class Navigator {
    private static Scene scene;

    private Navigator() {
    }

    public static void init(Scene mainScene) {
        scene = mainScene;
    }

    public static void showLogin() {
        setRoot("/fxml/login.fxml");
    }

    public static void showShell() {
        setRoot("/fxml/shell.fxml");
    }

    private static void setRoot(String fxml) {
        try {
            URL location = Navigator.class.getResource(fxml);
            if (location == null) {
                throw new IOException(fxml + " not found on classpath");
            }

            Parent root = (Parent)FXMLLoader.load(location);
            scene.setRoot(root);
        } catch (RuntimeException | IOException var3) {
            Alerts.error(scene.getWindow(), "Could not open screen " + fxml, var3);
        }
    }
}
