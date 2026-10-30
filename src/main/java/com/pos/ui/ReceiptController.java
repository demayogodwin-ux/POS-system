package com.pos.ui;

import com.pos.model.Sale;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

public class ReceiptController {
    private static final Color SUCCESS = Color.web("#34D399");
    private static final Color TEXT = Color.web("#ECEEF1");
    @FXML
    private StackPane checkCircle;
    @FXML
    private Label changeMessageLabel;
    @FXML
    private StackPane previewHolder;
    @FXML
    private Button printButton;
    @FXML
    private Button newSaleButton;
    private Stage stage;
    private ReceiptView receipt;

    public static void show(Window owner, Sale sale, ReceiptView.StoreInfo store, boolean autoPrint) throws IOException {
        FXMLLoader loader = new FXMLLoader(ReceiptController.class.getResource("/fxml/receipt.fxml"));
        Parent card = (Parent)loader.load();
        ReceiptController controller = (ReceiptController)loader.getController();
        controller.attach(Overlay.create(owner, card), sale, store, autoPrint);
        controller.stage.showAndWait();
    }

    @FXML
    private void initialize() {
        Node check = Icons.graphic(Icons.Icon.CHECK, SUCCESS);
        check.setScaleX(1.4);
        check.setScaleY(1.4);
        this.checkCircle.getChildren().add(check);
        this.printButton.setGraphic(Icons.graphic(Icons.Icon.PRINTER, TEXT));
    }

    private void attach(Stage dialogStage, Sale sale, ReceiptView.StoreInfo store, boolean autoPrint) {
        this.stage = dialogStage;
        this.changeMessageLabel.setText(ReceiptView.changeMessage(sale));
        this.receipt = new ReceiptView(sale, store);
        this.previewHolder.getChildren().add(this.receipt);
        this.stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER:
                case ESCAPE:
                    e.consume();
                    this.handleNewSale();
            }
        });
        this.stage.setOnShown(e -> {
            this.newSaleButton.requestFocus();
            if (autoPrint) {
                Platform.runLater(() -> ReceiptPrinter.print(this.receipt, this.stage, false));
            }
        });
    }

    @FXML
    private void handlePrint() {
        ReceiptPrinter.print(this.receipt, this.stage, true);
    }

    @FXML
    private void handleNewSale() {
        this.stage.close();
    }
}
