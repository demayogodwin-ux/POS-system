package com.pos.ui;

import com.pos.app.Background;
import com.pos.db.OrderMessageDao;
import com.pos.model.OnlineOrder;
import com.pos.model.OrderMessage;
import com.pos.model.User;
import com.pos.service.Session;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Chat with the customer about one online order. One window per order; it refreshes while open. */
public final class OrderChatWindow {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a");
    private static final Duration REFRESH_EVERY = Duration.seconds(3.0);
    private static final Map<Integer, OrderChatWindow> OPEN = new HashMap<>();
    private final OrderMessageDao messageDao = new OrderMessageDao();
    private final OnlineOrder order;
    private final Runnable onRead;
    private final Stage stage = new Stage();
    private final VBox messagesBox = new VBox(10.0);
    private final ScrollPane scroll = new ScrollPane(this.messagesBox);
    private final TextField input = new TextField();
    private final Button sendButton = new Button("Send");
    private final Timeline refresher = new Timeline(new KeyFrame(REFRESH_EVERY, e -> this.load()));
    private int lastShownId = -1;
    private boolean loading;

    /** Opens the chat for an order, or brings its window to the front. onRead runs after messages are marked as read. */
    public static void open(Window owner, OnlineOrder order, Runnable onRead) {
        OrderChatWindow existing = OPEN.get(order.getId());
        if (existing != null) {
            existing.stage.toFront();
            existing.input.requestFocus();
            return;
        }

        OrderChatWindow window = new OrderChatWindow(order, onRead);
        OPEN.put(order.getId(), window);
        window.show(owner);
    }

    private OrderChatWindow(OnlineOrder order, Runnable onRead) {
        this.order = order;
        this.onRead = onRead;
    }

    private void show(Window owner) {
        Label title = new Label("Order " + this.order.getOrderNo() + " - " + this.order.getCustomerName());
        title.getStyleClass().add("cart-title");
        Label subtitle = new Label(this.order.getCustomerPhone() + " - " + this.order.getType().label() + " - " + this.order.getStatus().label());
        subtitle.getStyleClass().add("muted");
        VBox header = new VBox(2.0, title, subtitle);

        this.messagesBox.setPadding(new Insets(4.0));
        this.messagesBox.getChildren().setAll(new Label("Loading messages..."));
        this.scroll.setFitToWidth(true);
        this.scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.scroll.getStyleClass().add("detail-scroll");
        // Keep the newest message in view as messages are added.
        this.messagesBox.heightProperty().addListener((obs, oldHeight, height) -> this.scroll.setVvalue(1.0));

        this.input.setPromptText("Write to the customer...");
        this.input.getStyleClass().add("text-field");
        this.input.setOnAction(e -> this.send());
        HBox.setHgrow(this.input, Priority.ALWAYS);
        this.sendButton.getStyleClass().add("btn-primary");
        this.sendButton.setDefaultButton(true);
        this.sendButton.setOnAction(e -> this.send());
        HBox composer = new HBox(10.0, this.input, this.sendButton);
        composer.setAlignment(Pos.CENTER_LEFT);

        BorderPane root = new BorderPane(this.scroll, header, null, composer, null);
        root.setPadding(new Insets(16.0));
        BorderPane.setMargin(this.scroll, new Insets(12.0, 0.0, 12.0, 0.0));
        root.getStyleClass().add("chat-window");

        Scene scene = new Scene(root, 440.0, 560.0);
        URL css = OrderChatWindow.class.getResource("/css/dark-pro.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        if (owner != null) {
            this.stage.initOwner(owner);
        }

        this.stage.setTitle("Chat - Order " + this.order.getOrderNo());
        this.stage.setScene(scene);
        this.stage.setOnHidden(e -> {
            this.refresher.stop();
            OPEN.remove(this.order.getId());
        });
        this.refresher.setCycleCount(Timeline.INDEFINITE);
        this.refresher.play();
        this.stage.show();
        this.input.requestFocus();
        this.load();
    }

    private void load() {
        if (this.loading) {
            return;
        }

        this.loading = true;
        int orderId = this.order.getId();
        Task<List<OrderMessage>> task = new Task<List<OrderMessage>>() {
            protected List<OrderMessage> call() throws Exception {
                OrderChatWindow.this.messageDao.markRead(orderId, true);
                return OrderChatWindow.this.messageDao.findForOrder(orderId);
            }
        };
        task.setOnSucceeded(e -> {
            this.loading = false;
            this.show(task.getValue());
        });
        task.setOnFailed(e -> {
            this.loading = false;
            if (this.lastShownId < 0) {
                this.messagesBox.getChildren().setAll(new Label("Messages could not be loaded. Retrying..."));
            }
        });
        Background.run(task);
    }

    private void show(List<OrderMessage> messages) {
        int newestId = messages.isEmpty() ? 0 : messages.get(messages.size() - 1).id();
        if (newestId == this.lastShownId) {
            return;
        }

        boolean customerWrote = this.lastShownId >= 0 && messages.stream().anyMatch(m -> m.id() > this.lastShownId && !m.fromStaff());
        this.lastShownId = newestId;
        this.messagesBox.getChildren().clear();
        if (messages.isEmpty()) {
            Label empty = new Label("No messages yet. Write to the customer about a problem with the order or the delivery.");
            empty.setWrapText(true);
            empty.getStyleClass().add("muted");
            this.messagesBox.getChildren().add(empty);
        }

        for (OrderMessage message : messages) {
            this.messagesBox.getChildren().add(bubble(message));
        }

        if (customerWrote) {
            Beeper.beep();
        }

        if (this.onRead != null) {
            Platform.runLater(this.onRead);
        }
    }

    private static HBox bubble(OrderMessage message) {
        Label text = new Label(message.body());
        text.setWrapText(true);
        text.getStyleClass().add("chat-text");
        String who = message.fromStaff() ? (message.staffName() == null ? "Store" : message.staffName()) : "Customer";
        Label meta = new Label(who + " - " + message.createdAt().format(TIME));
        meta.getStyleClass().addAll("muted", "chat-meta");
        VBox box = new VBox(4.0, text, meta);
        box.getStyleClass().addAll("chat-bubble", message.fromStaff() ? "chat-bubble-staff" : "chat-bubble-customer");
        box.setMaxWidth(320.0);
        HBox row = new HBox(box);
        row.setAlignment(message.fromStaff() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private void send() {
        String text = this.input.getText().trim();
        if (text.isEmpty() || this.sendButton.isDisabled()) {
            return;
        }

        if (text.length() > OrderMessage.MAX_LENGTH) {
            Alerts.info(this.stage, "Message is too long", "Keep it under " + OrderMessage.MAX_LENGTH + " characters.");
            return;
        }

        User staff = Session.getInstance().getCurrentUser();
        String staffName = staff == null ? null : staff.getFullName();
        int orderId = this.order.getId();
        this.sendButton.setDisable(true);
        Task<OrderMessage> task = new Task<OrderMessage>() {
            protected OrderMessage call() throws Exception {
                return OrderChatWindow.this.messageDao.add(orderId, true, staffName, text);
            }
        };
        task.setOnSucceeded(e -> {
            this.sendButton.setDisable(false);
            this.input.clear();
            this.input.requestFocus();
            this.load();
        });
        task.setOnFailed(e -> {
            this.sendButton.setDisable(false);
            Alerts.error(this.stage, "Message was not sent", task.getException());
        });
        Background.run(task);
    }
}
