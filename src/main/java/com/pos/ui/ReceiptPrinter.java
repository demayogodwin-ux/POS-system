package com.pos.ui;

import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

public final class ReceiptPrinter {
    private ReceiptPrinter() {
    }

    public static boolean print(Parent receipt, Window owner, boolean showDialog) {
        try {
            if (receipt.getScene() == null) {
                new Scene(new Group(new Node[]{receipt}));
                receipt.applyCss();
                receipt.layout();
            }

            PrinterJob job = PrinterJob.createPrinterJob();
            if (job == null) {
                Alerts.info(owner, "No printer found", "Install or connect a printer, then try again.");
                return false;
            } else if (showDialog && !job.showPrintDialog(owner)) {
                job.cancelJob();
                return false;
            } else {
                PageLayout layout = job.getJobSettings().getPageLayout();
                double width = receipt.getLayoutBounds().getWidth();
                double scale = width > 0.0 ? Math.min(1.0, layout.getPrintableWidth() / width) : 1.0;
                Scale fit = new Scale(scale, scale);
                receipt.getTransforms().add(fit);

                boolean var10;
                try {
                    if (!job.printPage(receipt)) {
                        job.cancelJob();
                        Alerts.info(owner, "Printing failed", "The printer did not accept the receipt.");
                        return false;
                    }

                    var10 = job.endJob();
                } finally {
                    receipt.getTransforms().remove(fit);
                }

                return var10;
            }
        } catch (RuntimeException var15) {
            Alerts.error(owner, "Could not print the receipt", var15);
            return false;
        }
    }
}
