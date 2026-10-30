package com.pos.app;

import com.pos.db.Schema;
import com.pos.db.SettingsDao;
import com.pos.ui.ThemeShowcase;
import com.pos.web.MobileServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Main extends Application {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    public static final String APP_TITLE = "My Store POS";
    public static final String STYLESHEET = "/css/dark-pro.css";
    public static final double WIDTH = 1440.0;
    public static final double HEIGHT = 900.0;
    public static final double MIN_WIDTH = 1024.0;
    public static final double MIN_HEIGHT = 680.0;
    private static final String[] FONT_FILES = new String[]{
        "Manrope-Regular.ttf", "Manrope-SemiBold.ttf", "Manrope-Bold.ttf", "Manrope-ExtraBold.ttf", "JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf"
    };

    public void start(Stage stage) {
        loadFonts();
        // Fit the window inside the usable screen area (minus the taskbar), with a margin, so the title bar stays visible.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(WIDTH, screen.getWidth() * 0.92);
        double height = Math.min(HEIGHT, screen.getHeight() * 0.85);
        Scene scene = new Scene(new StackPane(), width, height);
        URL css = Main.class.getResource("/css/dark-pro.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            LOG.warning("/css/dark-pro.css not found on classpath");
        }

        stage.setTitle("My Store POS");
        stage.setMinWidth(Math.min(MIN_WIDTH, width));
        stage.setMinHeight(Math.min(MIN_HEIGHT, height));
        stage.setScene(scene);
        Navigator.init(scene);
        if (this.getParameters().getRaw().contains("--showcase")) {
            scene.setRoot(new ThemeShowcase());
        } else {
            Navigator.showLogin();
        }

        stage.show();
        stage.setX(screen.getMinX() + Math.max(0.0, (screen.getWidth() - stage.getWidth()) / 2.0));
        stage.setY(screen.getMinY() + Math.max(0.0, (screen.getHeight() - stage.getHeight()) / 2.0));
        startMobileServer();
    }

    /** Updates the database for phone ordering, then serves the customer phone app on the store network. */
    private static void startMobileServer() {
        Task<Integer> task = new Task<Integer>() {
            protected Integer call() throws Exception {
                Schema.migrate();
                int port = MobileServer.DEFAULT_PORT;

                try {
                    port = Integer.parseInt(new SettingsDao().get("mobile_port", String.valueOf(MobileServer.DEFAULT_PORT)).trim());
                } catch (NumberFormatException e) {
                    LOG.warning("mobile_port setting is not a number, using " + port);
                }

                MobileServer.start(port);
                return port;
            }
        };
        task.setOnFailed(e -> LOG.log(Level.WARNING, "The phone ordering app could not be started", task.getException()));
        Background.run(task);
    }

    private static void loadFonts() {
        for (String file : FONT_FILES) {
            try (InputStream in = Main.class.getResourceAsStream("/fonts/" + file)) {
                if (in != null) {
                    Font font = Font.loadFont(in, 14.0);
                    if (font == null) {
                        LOG.warning("Font could not be loaded: /fonts/" + file);
                    } else {
                        LOG.info("Loaded font " + font.getName() + " (family \"" + font.getFamily() + "\")");
                    }
                } else {
                    LOG.warning("Font not found on classpath: /fonts/" + file);
                }
            } catch (IOException var9) {
                LOG.log(Level.WARNING, "Error reading font /fonts/" + file, (Throwable)var9);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
