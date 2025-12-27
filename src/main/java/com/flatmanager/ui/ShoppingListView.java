package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.ShoppingItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.InputStream;
import java.sql.*;
import java.util.*;

public class ShoppingListView {

    private BorderPane root;
    private String currentUser;
    private ObservableList<ShoppingItem> items;

    private VBox listContainer;

    // Combo für Käufer-Zuweisung beim Hinzufügen (als Feld, damit loadUsers sie aktualisieren kann)
    private ComboBox<String> assignOnAddCombo;

    // Benutzer-Display -> username (zum Mapping)
    private final Map<String, String> userDisplayToUsername = new LinkedHashMap<>();
    private final List<String> userDisplayList = new ArrayList<>();

    public ShoppingListView(String username) {
        this.currentUser = username;
        this.items = FXCollections.observableArrayList();
        createView();
        loadItems();
    }

    private void createView() {
        root = new BorderPane();
        root.getStyleClass().add("shopping-view");
        root.setPadding(new Insets(0));

        // Page header bar (full width)
        Label header = new Label("Einkaufsliste");
        header.getStyleClass().addAll("shopping-title");
        header.setWrapText(true);
        HBox pageHeader = new HBox(header);
        pageHeader.getStyleClass().add("page-header");
        pageHeader.setAlignment(Pos.CENTER);
        root.setTop(pageHeader);

        // Linke Spalte (Liste) — enthält Titel oben
        listContainer = new VBox(8);
        listContainer.setPadding(new Insets(10));
        listContainer.getStyleClass().add("columns");

        ScrollPane scrollPane = new ScrollPane(listContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        scrollPane.getStyleClass().add("scroll-pane");

        Label sectionTitle = new Label("Unsere Einkäufe");
        sectionTitle.getStyleClass().add("title");

        VBox centerBox = new VBox(8, sectionTitle, scrollPane);
        centerBox.setPadding(new Insets(10));
        centerBox.getStyleClass().add("content");
        centerBox.setFillWidth(true);
        HBox.setHgrow(centerBox, Priority.ALWAYS);

        // Rechte Spalte (Form) — enthält Titel oben und Form direkt darunter (bündig)
        Label formTitle = new Label("Neues Produkt hinzufügen");
        formTitle.getStyleClass().add("title");

        VBox form = new VBox(8);
        // Top padding null, damit Title und Felder bündig sind
        form.setPadding(new Insets(0, 8, 8, 8));
        form.setStyle("-fx-border-color: transparent; -fx-background-color: transparent;");
        form.setMaxWidth(Double.MAX_VALUE);
        // Ensure children expand horizontally to fill the form width
        form.setFillWidth(true);

        TextField itemField = new TextField();
        itemField.setPromptText("Name");
        itemField.getStyleClass().add("text-field");

        TextField quantityField = new TextField();
        quantityField.setPromptText("Menge (z. B. 2x, 500g)");
        quantityField.getStyleClass().add("text-field");

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(
                "Obst & Gemüse",
                "Backwaren",
                "Milchprodukte",
                "Eier",
                "Fleisch & Fisch",
                "Beilagen",
                "Snacks",
                "Getränke",
                "Tiefkühlprodukte",
                "Haushalt",
                "Sonstiges"
        );
        categoryCombo.setValue("Sonstiges");
        categoryCombo.getStyleClass().add("combo-box");
        categoryCombo.setMaxWidth(220);

        assignOnAddCombo = new ComboBox<>(FXCollections.observableArrayList(userDisplayList));
        assignOnAddCombo.setVisible(false);
        assignOnAddCombo.setMaxWidth(Double.MAX_VALUE);
        assignOnAddCombo.setPromptText("Mitbewohner auswählen");

        Button assignBuyerBtn = new Button();
        assignBuyerBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(assignBuyerBtn, Priority.ALWAYS);
        assignBuyerBtn.getStyleClass().add("icon-button");
        InputStream is = getClass().getResourceAsStream("/icons/Mitbewohner_icon.png");
        if (is != null) {
            Image img = new Image(is, 20, 20, true, true);
            ImageView iv = new ImageView(img);
            assignBuyerBtn.setGraphic(iv);
        } else {
            assignBuyerBtn.setText("Mitbewohner");
        }
        assignBuyerBtn.setTooltip(new Tooltip("Käufer zuweisen"));

        assignBuyerBtn.setOnAction(e -> {
            boolean now = !assignOnAddCombo.isVisible();
            assignOnAddCombo.setVisible(now);
            assignOnAddCombo.getItems().setAll(userDisplayList);
        });

        HBox categoryRow = new HBox(8, categoryCombo, assignBuyerBtn);
        categoryRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(categoryRow, Priority.ALWAYS);
        HBox.setHgrow(assignBuyerBtn, Priority.ALWAYS);

        Button saveBtn = new Button("Speichern");
        saveBtn.setWrapText(true);
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        // encourage the layout to stretch the button horizontally
        VBox.setVgrow(saveBtn, Priority.NEVER);
        saveBtn.getStyleClass().addAll("button", "button-primary");
        saveBtn.setMaxWidth(Double.MAX_VALUE);

        saveBtn.setOnAction(e -> {
            String name = itemField.getText().trim();
            String qty = quantityField.getText().trim();
            String cat = categoryCombo.getValue();

            if (!name.isEmpty()) {
                String purchasedFor = null;
                if (assignOnAddCombo.isVisible() && assignOnAddCombo.getValue() != null) {
                    purchasedFor = userDisplayToUsername.get(assignOnAddCombo.getValue());
                }
                addItem(name, qty.isEmpty() ? "1" : qty, cat, purchasedFor);
                itemField.clear();
                quantityField.clear();
                categoryCombo.setValue("Sonstiges");
                assignOnAddCombo.setVisible(false);
                assignOnAddCombo.getSelectionModel().clearSelection();
                loadItems();
            } else {
                showAlert("Bitte einen Namen eingeben");
            }
        });

        form.getChildren().addAll(itemField, quantityField, categoryRow, assignOnAddCombo, saveBtn);

        // Clear Buttons — gleiche Breite wie Save (Binding erfolgt weiter unten)
        Button clearCompletedBtn = new Button("Erledigte leeren");
        clearCompletedBtn.setWrapText(true);
        clearCompletedBtn.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(clearCompletedBtn, Priority.NEVER);
        clearCompletedBtn.getStyleClass().addAll("button");
        clearCompletedBtn.setMaxWidth(Double.MAX_VALUE);
        clearCompletedBtn.setOnAction(e -> {
            clearCompleted();
            loadItems();
        });

        Button clearBtn = new Button("Liste leeren");
        clearBtn.setWrapText(true);
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(clearBtn, Priority.NEVER);
        clearBtn.getStyleClass().addAll("button", "button-danger");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setOnAction(e -> {
            clearList();
            loadItems();
        });

        // Form-Title oben in der rechten Spalte, direkt über dem Formular (bündig)
        // Die Clear-Buttons werden INSIDE des `form` platziert, damit sie dieselbe Einrückung/Breite wie der Save-Button haben.
        form.getChildren().addAll(clearCompletedBtn, clearBtn);
        VBox rightBox = new VBox(8);
        rightBox.setPadding(new Insets(10));
        rightBox.getStyleClass().add("column");
        rightBox.setFillWidth(true);
        HBox.setHgrow(rightBox, Priority.ALWAYS);

        rightBox.getChildren().addAll(formTitle, form);

        HBox mainColumns = new HBox(12, centerBox, rightBox);
        mainColumns.setPadding(new Insets(0));
        mainColumns.setAlignment(Pos.TOP_LEFT);

        root.setCenter(mainColumns);

        // Ensure form fills the rightBox usable width but don't force preferred widths
        form.prefWidthProperty().bind(rightBox.widthProperty().subtract(16));
        // Revert to computed preferred width so buttons use natural/CSS sizing
        saveBtn.setPrefWidth(Region.USE_COMPUTED_SIZE);
        clearCompletedBtn.setPrefWidth(Region.USE_COMPUTED_SIZE);
        clearBtn.setPrefWidth(Region.USE_COMPUTED_SIZE);

        // Ensure the buttons are direct children of the form (remove any HBox wrappers if present)
        form.getChildren().removeIf(node -> node instanceof HBox && ((HBox) node).getChildren().contains(saveBtn));
        form.getChildren().removeIf(node -> node instanceof HBox && ((HBox) node).getChildren().contains(clearCompletedBtn));
        form.getChildren().removeIf(node -> node instanceof HBox && ((HBox) node).getChildren().contains(clearBtn));
        // Remove possible duplicate button entries then add the plain buttons in order
        form.getChildren().remove(saveBtn);
        form.getChildren().remove(clearCompletedBtn);
        form.getChildren().remove(clearBtn);
        form.getChildren().add(saveBtn);
        form.getChildren().addAll(clearCompletedBtn, clearBtn);

        // lade Benutzerliste initial (nachdem assignOnAddCombo existiert)
        loadUsers();
    }

    private void loadUsers() {
        userDisplayToUsername.clear();
        userDisplayList.clear();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT username, name FROM users ORDER BY name COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String username = rs.getString("username");
                String name = rs.getString("name");
                String display;

                // Fallback: wenn kein Anzeige-Name gesetzt ist, nutze username
                if (name == null || name.trim().isEmpty()) {
                    display = username;
                } else if ("admin".equalsIgnoreCase(username)) {
                    // Für den Admin nur den festgelegten Namen anzeigen (ohne "(admin)")
                    display = name;
                } else {
                    // Für alle anderen: "Name (username)"
                    display = name + " (" + username + ")";
                }

                userDisplayToUsername.put(display, username);
                userDisplayList.add(display);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Aktualisiere das Combo (falls bereits initialisiert)
        if (assignOnAddCombo != null) {
            assignOnAddCombo.getItems().setAll(userDisplayList);
        }
    }

    private void loadItems() {
        items.clear();

        try (Connection conn = DatabaseManager.getConnection()) {
            ensureColumnExists(conn, "shopping_items", "category", "TEXT", "'Sonstiges'");
            ensureColumnExists(conn, "shopping_items", "purchased_for", "TEXT", "NULL");
            ensureColumnExists(conn, "shopping_items", "purchased", "INTEGER", "0");

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM shopping_items ORDER BY category, item_name")) {

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String itemName = rs.getString("item_name");
                    String quantity = rs.getString("quantity");
                    String addedBy = rs.getString("added_by");
                    String category = rs.getString("category");
                    String purchasedFor = rs.getString("purchased_for");
                    boolean purchased = rs.getInt("purchased") == 1;

                    ShoppingItem it = new ShoppingItem(id, itemName, quantity, addedBy, category, purchasedFor, purchased);
                    items.add(it);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Laden der Artikel: " + e.getMessage());
        }

        loadUsers();
        rebuildCategoryLayout();
    }

    private void ensureColumnExists(Connection conn, String tableName, String columnName, String columnType, String defaultValueSql) {
        String pragma = "PRAGMA table_info(" + tableName + ")";
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(pragma)) {

            boolean found = false;
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equalsIgnoreCase(name)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                String alter = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType + " DEFAULT " + defaultValueSql;
                try (Statement s2 = conn.createStatement()) {
                    s2.executeUpdate(alter);
                } catch (SQLException ex) {
                    System.out.println("[DB] Could not add column " + columnName + ": " + ex.getMessage());
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB] Could not inspect table " + tableName + ": " + e.getMessage());
        }
    }

    private void rebuildCategoryLayout() {
        listContainer.getChildren().clear();

        Map<String, List<ShoppingItem>> grouped = new LinkedHashMap<>();

        for (ShoppingItem item : items) {
            String cat = item.getCategory() == null ? "Sonstiges" : item.getCategory();
            grouped.computeIfAbsent(cat, c -> new ArrayList<>()).add(item);
        }

        final double qtyWidth = 18;
        final double spacingBetweenQtyAndName = 4;
        final double gapBetweenCheckAndContent = 0;

        for (String category : grouped.keySet()) {

            Label catLabel = new Label(category);
            catLabel.setWrapText(true);
            catLabel.setMaxWidth(Double.MAX_VALUE);
            catLabel.setPadding(new Insets(4, 0, 4, 0));
            catLabel.getStyleClass().add("title");

            VBox itemBox = new VBox(4);

            for (ShoppingItem item : grouped.get(category)) {
                HBox row = new HBox(0);
                row.setAlignment(Pos.CENTER_LEFT);

                CheckBox check = new CheckBox();
                check.setSelected(item.isPurchased());
                check.setPadding(Insets.EMPTY);
                check.setStyle("-fx-padding: 0; -fx-background-insets: 0;");
                check.setMinWidth(18);
                check.setPrefWidth(18);
                check.setMaxWidth(18);
                HBox.setMargin(check, Insets.EMPTY);
                check.setTranslateY(-9);

                Label qty = new Label(item.getQuantity() == null ? "" : item.getQuantity());
                qty.getStyleClass().add("small-text");
                qty.setMinWidth(qtyWidth);
                qty.setPrefWidth(qtyWidth);
                qty.setMaxWidth(qtyWidth);
                qty.setAlignment(Pos.CENTER_RIGHT);

                Label name = new Label(item.getItemName());
                name.getStyleClass().add("small-text");
                name.setWrapText(true);
                name.setMaxWidth(400);

                HBox topLine = new HBox(spacingBetweenQtyAndName, qty, name);
                topLine.setAlignment(Pos.CENTER_LEFT);
                topLine.setPadding(Insets.EMPTY);

                Label purchaserLabel = new Label();
                purchaserLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
                purchaserLabel.setVisible(false);
                purchaserLabel.setPadding(new Insets(4, 0, 0, spacingBetweenQtyAndName));

                if (item.getPurchasedFor() != null && !item.getPurchasedFor().isEmpty()) {
                    String displayFor = userDisplayToUsername.entrySet()
                            .stream()
                            .filter(e -> e.getValue().equals(item.getPurchasedFor()))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(item.getPurchasedFor());
                    purchaserLabel.setText("Für: " + displayFor);
                    purchaserLabel.setVisible(true);
                }

                check.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    item.setPurchased(newVal);
                    if (newVal) {
                        if (item.getPurchasedFor() != null && !item.getPurchasedFor().isEmpty()) {
                            updatePurchased(item.getId(), true, item.getPurchasedFor());
                        } else {
                            updatePurchased(item.getId(), true);
                        }
                    } else {
                        updatePurchased(item.getId(), false);
                    }
                    loadItems();
                });

                VBox contentBox = new VBox(0, topLine, purchaserLabel);
                contentBox.setAlignment(Pos.CENTER_LEFT);
                contentBox.setPadding(Insets.EMPTY);
                HBox.setMargin(contentBox, new Insets(0, 0, 0, gapBetweenCheckAndContent));
                HBox.setHgrow(contentBox, Priority.ALWAYS);

                row.getChildren().addAll(check, contentBox);
                itemBox.getChildren().add(row);
            }

            VBox wrapper = new VBox(4, catLabel, itemBox);
            listContainer.getChildren().add(wrapper);
        }
    }

    private void addItem(String itemName, String quantity, String category, String purchasedFor) {
        String sql = "INSERT INTO shopping_items (item_name, quantity, added_by, category, purchased_for, purchased) VALUES (?, ?, ?, ?, ?, 0)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemName);
            pstmt.setString(2, quantity);
            pstmt.setString(3, currentUser);
            pstmt.setString(4, category);
            if (purchasedFor != null) pstmt.setString(5, purchasedFor);
            else pstmt.setNull(5, Types.VARCHAR);
            pstmt.executeUpdate();

            // notify dashboard for immediate refresh
            try { com.flatmanager.ui.DashboardScreen.notifyRefreshNow(); } catch (Throwable ignore) {}

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Hinzufügen");
        }
    }

    private void updatePurchased(int id, boolean purchased) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("UPDATE shopping_items SET purchased = ? WHERE id = ?")) {

            pstmt.setInt(1, purchased ? 1 : 0);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();

            try { com.flatmanager.ui.DashboardScreen.notifyRefreshNow(); } catch (Throwable ignore) {}

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updatePurchased(int id, boolean purchased, String purchasedFor) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE shopping_items SET purchased = ?, purchased_for = ? WHERE id = ?"
             )) {

            pstmt.setInt(1, purchased ? 1 : 0);
            if (purchasedFor != null) pstmt.setString(2, purchasedFor);
            else pstmt.setNull(2, Types.VARCHAR);
            pstmt.setInt(3, id);
            pstmt.executeUpdate();

            try { com.flatmanager.ui.DashboardScreen.notifyRefreshNow(); } catch (Throwable ignore) {}

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void clearCompleted() {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM shopping_items WHERE purchased = 1")) {
            int deleted = ps.executeUpdate();
            System.out.println("[DB] Gelöschte erledigte Einträge: " + deleted);
            try { com.flatmanager.ui.DashboardScreen.notifyRefreshNow(); } catch (Throwable ignore) {}
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Löschen der erledigten Einträge");
        }
    }

    private void clearList() {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DELETE FROM shopping_items");
            try { com.flatmanager.ui.DashboardScreen.notifyRefreshNow(); } catch (Throwable ignore) {}

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Leeren der Liste");
        }
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
        if (com.flatmanager.App.getPrimaryStage() != null) a.initOwner(com.flatmanager.App.getPrimaryStage());
        a.showAndWait();
    }

    public Region getView() {
        return root;
    }
}

