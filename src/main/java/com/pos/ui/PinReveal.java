package com.pos.ui;

import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;

/**
 * Eye button on a PIN box. The PIN lives in the PasswordField; the TextField stacked on top of it
 * shows the same digits while the eye is on. Both take at most 6 digits.
 */
final class PinReveal {
    private static final Color MUTED = Color.web("#9AA3AE");
    private final PasswordField hidden;
    private final TextField shown;
    private final Button toggle;

    PinReveal(PasswordField hidden, TextField shown, Button toggle) {
        this.hidden = hidden;
        this.shown = shown;
        this.toggle = toggle;
        hidden.setTextFormatter(pinFormatter());
        shown.setTextFormatter(pinFormatter());
        shown.textProperty().bindBidirectional(hidden.textProperty());
        shown.disableProperty().bind(hidden.disableProperty());
        toggle.disableProperty().bind(hidden.disableProperty());
        toggle.setFocusTraversable(false);
        toggle.setOnAction(e -> this.toggle());
        this.setShown(false);
    }

    private static TextFormatter<String> pinFormatter() {
        return new TextFormatter<String>(change -> change.getControlNewText().matches("\\d{0,6}") ? change : null);
    }

    /** Whichever box is on screen: the dots, or the plain digits while the eye is on. */
    TextInputControl active() {
        return this.shown.isVisible() ? this.shown : this.hidden;
    }

    void requestFocus() {
        this.active().requestFocus();
    }

    /** Applies a pseudo-class such as :error to both boxes, so it stays visible after toggling. */
    void pseudoClassStateChanged(PseudoClass pseudoClass, boolean active) {
        this.hidden.pseudoClassStateChanged(pseudoClass, active);
        this.shown.pseudoClassStateChanged(pseudoClass, active);
    }

    /** Runs the listener whenever either box gains focus. */
    void onFocused(Runnable listener) {
        for (TextInputControl box : new TextInputControl[]{this.hidden, this.shown}) {
            box.focusedProperty().addListener((obs, was, focused) -> {
                if (focused) {
                    listener.run();
                }
            });
        }
    }

    private void toggle() {
        TextInputControl from = this.active();
        boolean hadFocus = from.isFocused();
        int caret = from.getCaretPosition();
        this.setShown(!this.shown.isVisible());
        TextInputControl to = this.active();
        to.positionCaret(caret);
        if (hadFocus) {
            to.requestFocus();
        }
    }

    private void setShown(boolean visible) {
        String label = visible ? "Hide PIN" : "Show PIN";
        this.shown.setVisible(visible);
        this.hidden.setVisible(!visible);
        this.toggle.setGraphic(Icons.graphic(visible ? Icons.Icon.EYE_OFF : Icons.Icon.EYE, MUTED));
        this.toggle.setTooltip(new Tooltip(label));
        this.toggle.setAccessibleText(label);
    }
}
