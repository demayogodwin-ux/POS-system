package com.pos.ui;

import java.net.URL;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

final class Overlay {
    private Overlay() {
    }

    static Stage create(Window owner, Parent card) {
        if (card instanceof Region region) {
            region.setMaxSize(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        }

        StackPane scrim = new StackPane(new Node[]{card});
        scrim.getStyleClass().add("scrim");
        Scene scene = new Scene(scrim, Color.TRANSPARENT);
        URL css = Overlay.class.getResource("/css/dark-pro.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("My Store POS");
        stage.setScene(scene);
        Scene ownerScene = owner.getScene();
        stage.setX(owner.getX() + ownerScene.getX());
        stage.setY(owner.getY() + ownerScene.getY());
        stage.setWidth(ownerScene.getWidth());
        stage.setHeight(ownerScene.getHeight());
        return stage;
    }
}
