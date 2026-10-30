package com.pos.ui;

import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class ThemeShowcase extends BorderPane {
    private static final Color TEXT = Color.web("#ECEEF1");
    private static final Color MUTED = Color.web("#9AA3AE");
    private static final Color ACCENT = Color.web("#F5A524");
    private static final Color DANGER = Color.web("#F87171");
    private static final String[][] SWATCHES = new String[][]{
        {"-pos-bg", "#121417"},
        {"-pos-rail", "#0C0D10"},
        {"-pos-panel", "#17191D"},
        {"-pos-surface", "#1C1F24"},
        {"-pos-surface-2", "#1F2227"},
        {"-pos-raised", "#23272E"},
        {"-pos-border", "#2A2E35"},
        {"-pos-border-strong", "#3A404A"},
        {"-pos-text", "#ECEEF1"},
        {"-pos-muted", "#9AA3AE"},
        {"-pos-accent", "#F5A524"},
        {"-pos-on-accent", "#1A1300"},
        {"-pos-success", "#34D399"},
        {"-pos-danger", "#F87171"},
        {"-pos-paper", "#F5F3EE"},
        {"-pos-ink", "#1C1C1C"}
    };

    public ThemeShowcase() {
        this.setLeft(this.buildNavRail());
        ScrollPane scroll = new ScrollPane(this.buildContent());
        scroll.setFitToWidth(true);
        this.setCenter(scroll);
    }

    private VBox buildNavRail() {
        Label logo = new Label("MS");
        logo.getStyleClass().add("logo-badge");
        VBox.setMargin(logo, new Insets(0.0, 0.0, 16.0, 0.0));
        ToggleGroup group = new ToggleGroup();
        ToggleButton sale = navItem("Sale", Icons.Icon.SALE, group);
        ToggleButton products = navItem("Products", Icons.Icon.PRODUCTS, group);
        ToggleButton history = navItem("History", Icons.Icon.HISTORY, group);
        ToggleButton settings = navItem("Settings", Icons.Icon.SETTINGS, group);
        sale.setSelected(true);
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button logout = new Button("Log out", Icons.graphic(Icons.Icon.LOGOUT, MUTED));
        logout.getStyleClass().add("nav-item");
        VBox rail = new VBox(new Node[]{logo, sale, products, history, settings, spacer, logout});
        rail.getStyleClass().add("nav-rail");
        return rail;
    }

    private static ToggleButton navItem(String text, Icons.Icon icon, ToggleGroup group) {
        ToggleButton item = new ToggleButton(text, Icons.graphic(icon, MUTED));
        item.getStyleClass().add("nav-item");
        item.setToggleGroup(group);
        return item;
    }

    private VBox buildContent() {
        Label title = new Label("Theme Showcase");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Temporary screen - compare with the Components panel in Figma.");
        subtitle.getStyleClass().add("muted");
        VBox page = new VBox(
            new Node[]{
                new VBox(4.0, new Node[]{title, subtitle}),
                section("Colors", this.colors()),
                section("Typography", this.typography()),
                section("Buttons", this.buttons()),
                section("Inputs", this.inputs()),
                section("Chips", this.chips()),
                section("Cards", this.cards()),
                section("Table", this.table()),
                section("Total due and switch", this.totalAndSwitch()),
                section("Icons", this.icons()),
                section("Dialog", this.dialogs())
            }
        );
        page.getStyleClass().add("page");
        return page;
    }

    private static VBox section(String name, Node content) {
        Label heading = new Label(name.toUpperCase());
        heading.getStyleClass().add("stat-label");
        return new VBox(12.0, new Node[]{heading, content});
    }

    private static FlowPane flow() {
        FlowPane flow = new FlowPane(12.0, 12.0);
        flow.setAlignment(Pos.CENTER_LEFT);
        return flow;
    }

    private static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    private Node colors() {
        FlowPane flow = flow();

        for (String[] swatch : SWATCHES) {
            Region chip = new Region();
            chip.setPrefSize(140.0, 56.0);
            chip.setStyle("-fx-background-color: " + swatch[0] + "; -fx-background-radius: 10; -fx-border-color: -pos-border; -fx-border-radius: 10;");
            Label name = label(swatch[0], "mono");
            name.setStyle("-fx-font-size: 12px;");
            Label hex = label(swatch[1], "mono", "muted");
            hex.setStyle("-fx-font-size: 12px;");
            VBox box = new VBox(6.0, new Node[]{chip, name, hex});
            box.setPrefWidth(140.0);
            flow.getChildren().add(box);
        }

        return flow;
    }

    private Node typography() {
        Label semibold = label("Manrope SemiBold 14 - product names");
        semibold.setStyle("-fx-font-weight: 600;");
        Label bold = label("Manrope Bold 14 - emphasis");
        bold.setStyle("-fx-font-weight: 700;");
        VBox stat = new VBox(4.0, new Node[]{label("ITEMS IN CART", "stat-label"), label("1,284.50", "stat-value")});
        return new VBox(
            8.0,
            new Node[]{
                label("Title - Manrope ExtraBold 22", "title"),
                label("Body - Manrope Regular 14"),
                semibold,
                bold,
                label("Muted - secondary information", "muted"),
                label("SKU-000123   x 3   1,250.00   (JetBrains Mono)", "mono"),
                label("Danger - out of stock", "text-danger"),
                stat
            }
        );
    }

    private Node buttons() {
        Button primary = new Button("Charge");
        primary.getStyleClass().add("btn-primary");
        Button primaryIcon = new Button("Complete sale", Icons.graphic(Icons.Icon.CHECK, ACCENT));
        primaryIcon.getStyleClass().add("btn-primary");
        Button secondary = new Button("Hold");
        secondary.getStyleClass().add("btn-secondary");
        Button secondaryIcon = new Button("Print receipt", Icons.graphic(Icons.Icon.PRINTER, TEXT));
        secondaryIcon.getStyleClass().add("btn-secondary");
        Button ghost = new Button("Cancel");
        ghost.getStyleClass().add("btn-ghost");
        Button danger = new Button("Remove", Icons.graphic(Icons.Icon.TRASH, DANGER));
        danger.getStyleClass().addAll(new String[]{"btn-ghost", "text-danger"});
        Button disabled = new Button("Disabled");
        disabled.getStyleClass().add("btn-primary");
        disabled.setDisable(true);
        FlowPane flow = flow();
        flow.getChildren().addAll(new Node[]{primary, primaryIcon, secondary, secondaryIcon, ghost, danger, disabled});
        return flow;
    }

    private Node inputs() {
        TextField search = new TextField();
        search.setPromptText("Search products or scan a barcode");
        search.setPrefWidth(320.0);
        TextField filled = new TextField("Coca-Cola 1.5L");
        filled.setPrefWidth(240.0);
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.setPrefWidth(240.0);
        ComboBox<String> category = new ComboBox(FXCollections.observableArrayList(new String[]{"Beverages", "Snacks", "Household", "Personal care"}));
        category.setPromptText("Category");
        category.setPrefWidth(220.0);
        FlowPane flow = flow();
        flow.getChildren().addAll(new Node[]{search, filled, password, category});
        return flow;
    }

    private Node chips() {
        FlowPane flow = flow();
        flow.getChildren()
            .addAll(
                new Node[]{label("Paid", "chip-success"), label("Low stock", "chip-warning"), label("Voided", "chip-danger"), label("Draft", "chip-neutral")}
            );
        return flow;
    }

    private Node cards() {
        FlowPane flow = flow();
        flow.getChildren().addAll(new Node[]{statCard("TODAY'S SALES", "12,480.00"), statCard("TRANSACTIONS", "86"), statCard("AVERAGE TICKET", "145.12")});
        return flow;
    }

    private static VBox statCard(String name, String value) {
        VBox card = new VBox(new Node[]{label(name, "stat-label"), label(value, "stat-value")});
        card.getStyleClass().add("card");
        card.setPrefWidth(260.0);
        return card;
    }

    private Node table() {
        TableView<ThemeShowcase.Row> table = new TableView(
            FXCollections.observableArrayList(
                new ThemeShowcase.Row[]{
                    new ThemeShowcase.Row("4800016644290", "Coca-Cola 1.5L", "24", "75.00", "In stock"),
                    new ThemeShowcase.Row("4800194104341", "Piattos Cheese 85g", "6", "38.50", "Low stock"),
                    new ThemeShowcase.Row("4806502160121", "Safeguard Soap 135g", "0", "52.75", "Out of stock"),
                    new ThemeShowcase.Row("4800024555103", "Nescafe Classic 50g", "40", "96.00", "In stock"),
                    new ThemeShowcase.Row("4801981116072", "Lucky Me Pancit Canton", "120", "16.25", "In stock")
                }
            )
        );
        table.getColumns().add(column("CODE", ThemeShowcase.Row::code, 180.0, "mono"));
        table.getColumns().add(column("PRODUCT", ThemeShowcase.Row::name, 280.0));
        table.getColumns().add(column("QTY", ThemeShowcase.Row::qty, 90.0, "mono", "numeric"));
        table.getColumns().add(column("PRICE", ThemeShowcase.Row::price, 120.0, "mono", "numeric"));
        TableColumn<ThemeShowcase.Row, String> status = column("STATUS", ThemeShowcase.Row::status, 140.0);
        status.setCellFactory(col -> new TableCell<ThemeShowcase.Row, String>() {
            private final Label chip = new Label();

            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                this.setText(null);
                if (!empty && item != null) {
                    this.chip.setText(item);
                    ObservableList var10000 = this.chip.getStyleClass();
                    String[] var10001 = new String[]{"label", null};

                    var10001[1] = switch (item) {
                        case "In stock" -> "chip-success";
                        case "Low stock" -> "chip-warning";
                        case "Out of stock" -> "chip-danger";
                        default -> "chip-neutral";
                    };
                    var10000.setAll(var10001);
                    this.setGraphic(this.chip);
                } else {
                    this.setGraphic(null);
                }
            }
        });
        table.getColumns().add(status);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(308.0);
        return table;
    }

    private static TableColumn<ThemeShowcase.Row, String> column(String title, Function<ThemeShowcase.Row, String> value, double width, String... styleClasses) {
        TableColumn<ThemeShowcase.Row, String> column = new TableColumn(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply((ThemeShowcase.Row)data.getValue())));
        column.setPrefWidth(width);
        column.getStyleClass().addAll(styleClasses);
        return column;
    }

    private Node totalAndSwitch() {
        VBox total = new VBox(new Node[]{label("TOTAL DUE", "caption"), label("1,284.50", "amount")});
        total.getStyleClass().add("total-due");
        total.setPrefWidth(380.0);
        total.setMaxWidth(380.0);
        VBox switches = new VBox(16.0, new Node[]{switchRow("Print receipt automatically", true), switchRow("Show low-stock warnings", false)});
        HBox row = new HBox(32.0, new Node[]{total, switches});
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox switchRow(String text, boolean on) {
        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("switch");
        toggle.setSelected(on);
        HBox row = new HBox(12.0, new Node[]{toggle, new Label(text)});
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node icons() {
        FlowPane textIcons = flow();
        FlowPane accentIcons = flow();

        for (Icons.Icon icon : Icons.Icon.values()) {
            VBox tile = new VBox(8.0, new Node[]{Icons.graphic(icon, TEXT), label(icon.name(), "muted")});
            tile.setAlignment(Pos.CENTER);
            tile.getStyleClass().add("card");
            tile.setPrefWidth(110.0);
            textIcons.getChildren().add(tile);
            accentIcons.getChildren().add(Icons.graphic(icon, ACCENT));
        }

        accentIcons.setHgap(20.0);
        return new VBox(16.0, new Node[]{textIcons, accentIcons});
    }

    private Node dialogs() {
        Button info = new Button("Show info Alert");
        info.getStyleClass().add("btn-secondary");
        info.setOnAction(e -> this.showAlert(AlertType.INFORMATION, "Sale completed", "Receipt #000123 was saved."));
        Button error = new Button("Show error Alert");
        error.getStyleClass().addAll(new String[]{"btn-ghost", "text-danger"});
        error.setOnAction(e -> this.showAlert(AlertType.ERROR, "Could not save the sale", "Communications link failure. Check that MySQL is running."));
        Button confirm = new Button("Show confirm Alert");
        confirm.getStyleClass().add("btn-secondary");
        confirm.setOnAction(e -> this.showAlert(AlertType.CONFIRMATION, "Void this sale?", "This cannot be undone."));
        FlowPane flow = flow();
        flow.getChildren().addAll(new Node[]{info, error, confirm});
        return flow;
    }

    private void showAlert(AlertType type, String header, String content) {
        Alert alert = new Alert(type);
        alert.initOwner(this.getScene().getWindow());
        alert.setTitle("My Store POS");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().getStylesheets().addAll(this.getScene().getStylesheets());
        alert.showAndWait();
    }

    private record Row(String code, String name, String qty, String price, String status) {
    }
}
