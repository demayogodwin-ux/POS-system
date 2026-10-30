package com.pos.ui;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

public final class Icons {
    public static final double SIZE = 24.0;
    public static final double STROKE_WIDTH = 2.0;

    private Icons() {
    }

    public static SVGPath svg(Icons.Icon icon, Paint color) {
        SVGPath svg = new SVGPath();
        svg.setContent(icon.path());
        svg.setFill(Color.TRANSPARENT);
        svg.setStroke(color);
        svg.setStrokeWidth(2.0);
        svg.setStrokeLineCap(StrokeLineCap.ROUND);
        svg.setStrokeLineJoin(StrokeLineJoin.ROUND);
        svg.getStyleClass().add("icon");
        return svg;
    }

    public static Pane graphic(Icons.Icon icon, Paint color) {
        Pane box = new Pane(new Node[]{svg(icon, color)});
        box.setMinSize(24.0, 24.0);
        box.setPrefSize(24.0, 24.0);
        box.setMaxSize(24.0, 24.0);
        box.getStyleClass().add("icon-box");
        return box;
    }

    public static enum Icon {
        SALE("M6 6h15l-1.5 9h-12z M6 6L5 3H2 M9 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z M18 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z"),
        PRODUCTS("M3 7l9-4 9 4-9 4-9-4z M3 7v10l9 4 9-4V7 M12 11v10"),
        HISTORY("M3 12a9 9 0 1 0 9-9 M3 4v5h5 M12 7v5l3 3"),
        SETTINGS("M4 6h16 M4 12h16 M4 18h16 M16 4v4 M8 10v4 M14 16v4"),
        LOGOUT("M15 4h4v16h-4 M10 8l-4 4 4 4 M6 12h10"),
        SCAN("M4 6v12 M8 6v12 M11 6v12 M15 6v12 M18 6v12 M20 6v12"),
        SEARCH("M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-3.5-3.5"),
        PLUS("M12 5v14 M5 12h14"),
        MINUS("M5 12h14"),
        EDIT("M4 20h4L19 9l-4-4L4 16v4z"),
        TRASH("M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13"),
        PRINTER("M6 9V3h12v6 M6 18H4v-7h16v7h-2 M7 14h10v7H7z"),
        CHECK("M5 12l5 5 9-10"),
        CLOSE("M6 6l12 12 M18 6L6 18"),
        USER("M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0"),
        PHONE("M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z M11 18h2"),
        EYE("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"),
        EYE_OFF("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M4 4l16 16");

        private final String path;

        private Icon(String path) {
            this.path = path;
        }

        public String path() {
            return this.path;
        }
    }
}
