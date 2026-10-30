package com.pos.ui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javax.imageio.ImageIO;

/** Shows a link as a QR code customers can scan with their phone camera, and saves it as a PNG for printing. */
public final class QrCodeDialog {
    private static final int SCREEN_SCALE = 8;
    private static final int PRINT_SCALE = 20;

    private QrCodeDialog() {
    }

    public static void show(Window owner, String title, String link) {
        BitMatrix matrix;
        try {
            matrix = encode(link);
        } catch (WriterException e) {
            Alerts.error(owner, "The QR code could not be made", e);
            return;
        }

        ImageView code = new ImageView(toImage(matrix, SCREEN_SCALE));
        // White border so phones can find the code on the dark theme.
        StackPane frame = new StackPane(code);
        frame.setStyle("-fx-background-color: white; -fx-background-radius: 8;");
        frame.setMaxWidth(StackPane.USE_PREF_SIZE);

        Label linkLabel = new Label(link);
        linkLabel.getStyleClass().addAll("mono", "strong");
        Label hint = new Label("Scan with the phone camera. The phone must be on the same Wi-Fi as this PC.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        Button save = new Button("Save image...");
        save.getStyleClass().add("btn-secondary");
        save.setOnAction(e -> save(owner, matrix));

        VBox content = new VBox(14.0, frame, linkLabel, hint, save);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(8.0));
        content.setPrefWidth(Math.max(380.0, matrix.getWidth() * SCREEN_SCALE + 40.0));

        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        dialog.setTitle("My Store POS");
        dialog.setHeaderText(title);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Alerts.style(dialog);
        dialog.showAndWait();
    }

    private static BitMatrix encode(String text) throws WriterException {
        // Size 0 gives the smallest matrix (one pixel per module); the quiet zone comes from the frame instead.
        return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
            Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
    }

    private static WritableImage toImage(BitMatrix matrix, int scale) {
        WritableImage image = new WritableImage(matrix.getWidth() * scale, matrix.getHeight() * scale);
        PixelWriter pixels = image.getPixelWriter();

        for (int y = 0; y < (int)image.getHeight(); y++) {
            for (int x = 0; x < (int)image.getWidth(); x++) {
                pixels.setArgb(x, y, matrix.get(x / scale, y / scale) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        return image;
    }

    private static void save(Window owner, BitMatrix matrix) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save QR code");
        chooser.setInitialFileName("store-app-qr.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }

        int size = matrix.getWidth() * PRINT_SCALE;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, matrix.get(x / PRINT_SCALE, y / PRINT_SCALE) ? 0x000000 : 0xFFFFFF);
            }
        }

        try {
            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            Alerts.error(owner, "The QR code was not saved", e);
        }
    }
}
