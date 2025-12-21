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
        root.getStyleClass().add("app-shell");
        root.setPadding(new Insets(0));

        Label header = new Label("Einkaufsliste");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 26));
        header.setPadding(new Insets(12));
        header.getStyleClass().add("title");

        HBox topBar = new HBox();
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0));
        topBar.setSpacing(8);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.getChildren().addAll(header, spacer);
        root.setTop(topBar);

        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(18));
        centerBox.getStyleClass().add("content");

        Label sectionTitle = new Label("Unsere Einkäufe");
        sectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        sectionTitle.getStyleClass().add("title");

        listContainer = new VBox(18);
        listContainer.setPadding(new Insets(10));
        listContainer.getStyleClass().add("columns");

        ScrollPane scrollPane = new ScrollPane(listContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white; -fx-border-color: transparent;");
        scrollPane.getStyleClass().add("scroll-pane");

        centerBox.getChildren().addAll(sectionTitle, scrollPane);

        VBox rightBox = new VBox(12);
        rightBox.setPadding(new Insets(18));
        rightBox.getStyleClass().add("column");

        Label formTitle = new Label("Neues Produkt hinzufügen");
        formTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        formTitle.getStyleClass().add("title");

        VBox form = new VBox(8);
        form.setPadding(new Insets(12));
        form.setStyle(
                "-fx-border-color: #bdbdbd;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-color: #ffffff;"
        );

        TextField itemField = new TextField();
        itemField.setPromptText("Name");
        itemField.getStyleClass().add("text-field");

        TextField quantityField = new TextField();
        quantityField.setPromptText("Menge (z. B. 2x, 500g)");
        quantityField.getStyleClass().add("text-field");

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(
                "Milch & Käse",
                "Obst & Gemüse",
                "Brot & Gebäck",
                "Eier",
                "Beilagen",
                "Getränke",
                "Haushalt",
                "Sonstiges"
        );
        categoryCombo.setValue("Sonstiges");
        categoryCombo.getStyleClass().add("combo-box");
        categoryCombo.setMaxWidth(220);

        // Combo zum Auswählen des Käufers (als Feld)
        assignOnAddCombo = new ComboBox<>(FXCollections.observableArrayList(userDisplayList));
        assignOnAddCombo.setVisible(false);
        assignOnAddCombo.setMaxWidth(Double.MAX_VALUE);
        assignOnAddCombo.setPromptText("Mitbewohner auswählen");

        // Icon-Button ersetzt die Checkbox; füllt die freie Fläche neben dem Dropdown
        Button assignBuyerBtn = new Button();
        assignBuyerBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(assignBuyerBtn, Priority.ALWAYS);
        assignBuyerBtn.getStyleClass().add("icon-button");
        // Lade Icon
        InputStream is = getClass().getResourceAsStream("/icons/Mitbewohner.png");
        if (is != null) {
            Image img = new Image(is, 20, 20, true, true);
            ImageView iv = new ImageView(img);
            assignBuyerBtn.setGraphic(iv);
        } else {
            assignBuyerBtn.setText("");
        }
        assignBuyerBtn.setTooltip(new Tooltip("Käufer zuweisen"));

        // Klick toggelt die Sichtbarkeit der Käufer-Combo
        assignBuyerBtn.setOnAction(e -> {
            boolean now = !assignOnAddCombo.isVisible();
            assignOnAddCombo.setVisible(now);
            assignOnAddCombo.getItems().setAll(userDisplayList);
        });

        // Kategorie und Button in einer Zeile, Button füllt freie Fläche
        HBox categoryRow = new HBox(8, categoryCombo, assignBuyerBtn);
        categoryRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(categoryCombo, Priority.NEVER);
        HBox.setHgrow(assignBuyerBtn, Priority.ALWAYS);

        Button saveBtn = new Button("Speichern");
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

        Button clearBtn = new Button("Liste leeren");
        clearBtn.getStyleClass().addAll("button", "button-danger");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setOnAction(e -> {
            clearList();
            loadItems();
        });

        form.getChildren().addAll(itemField, quantityField, categoryRow, assignOnAddCombo, saveBtn);
        rightBox.getChildren().addAll(formTitle, form, clearBtn);

        HBox mainColumns = new HBox(12);
        mainColumns.getChildren().addAll(centerBox, rightBox);

        HBox.setHgrow(centerBox, Priority.ALWAYS);
        HBox.setHgrow(rightBox, Priority.ALWAYS);
        centerBox.setMaxWidth(Double.MAX_VALUE);
        rightBox.setMaxWidth(Double.MAX_VALUE);

        centerBox.prefWidthProperty().bind(root.widthProperty().subtract(36).divide(2));
        rightBox.prefWidthProperty().bind(root.widthProperty().subtract(36).divide(2));

        root.setCenter(mainColumns);

        root.setStyle("-fx-font-family: Arial; -fx-background-color: white;");

        // lade Benutzerliste initial (nachdem assignOnAddCombo existiert)
        loadUsers();
    }

    private void loadUsers() {
        userDisplayToUsername.clear();
        userDisplayList.clear();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT username, name FROM users ORDER BY username");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String username = rs.getString("username");
                String name = rs.getString("name");
                // Anzeige nur: eingetragener Name, ansonsten username
                String display = (name != null && !name.trim().isEmpty()) ? name : username;
                userDisplayToUsername.put(display, username);
                userDisplayList.add(display);
            }

            // falls Combo existiert, Items aktualisieren und Prompt setzen
            if (assignOnAddCombo != null) {
                assignOnAddCombo.getItems().setAll(userDisplayList);
                assignOnAddCombo.setPromptText("Mitbewohner auswählen");
            }

        } catch (SQLException e) {
            System.out.println("[DB] Fehler beim Laden der User: " + e.getMessage());
        }
    }

    private void loadItems() {
        items.clear();

        try (Connection conn = DatabaseManager.getConnection()) {
            ensureColumnExists(conn, "shopping_items", "category", "TEXT", "'Sonstiges'");
            ensureColumnExists(conn, "shopping_items", "purchased_for", "TEXT", "NULL");

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM shopping_items ORDER BY category, item_name")) {

                while (rs.next()) {
                    items.add(new ShoppingItem(
                            rs.getInt("id"),
                            rs.getString("item_name"),
                            rs.getString("quantity"),
                            rs.getString("added_by"),
                            rs.getString("category"),
                            rs.getString("purchased_for"),
                            rs.getInt("purchased") == 1
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Laden der Artikel: " + e.getMessage());
        }

        // (re)lade Benutzeranzeige, falls sich DB geändert hat
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
                    System.out.println("[DB] Added missing column " + columnName + " to " + tableName);
                } catch (SQLException ex) {
                    System.out.println("[DB] Failed to add column " + columnName + ": " + ex.getMessage());
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
            grouped.computeIfAbsent(item.getCategory() == null ? "Sonstiges" : item.getCategory(), c -> new ArrayList<>()).add(item);
        }

        for (String category : grouped.keySet()) {

            Label catLabel = new Label(category);
            catLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
            catLabel.setPadding(new Insets(4, 0, 4, 0));
            catLabel.getStyleClass().add("title");

            VBox itemBox = new VBox(6);

            for (ShoppingItem item : grouped.get(category)) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);

                CheckBox check = new CheckBox();
                check.setSelected(item.isPurchased());

                Label qty = new Label(item.getQuantity());
                qty.setFont(Font.font(14));

                Label name = new Label(item.getItemName());
                name.setFont(Font.font(14));

                // Label für Käuferanzeige (klein unter dem Item)
                Label purchaserLabel = new Label();
                purchaserLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
                purchaserLabel.setVisible(false);

                // Wenn bereits ein purchased_for gesetzt ist, zeige ihn unter dem Item
                if (item.getPurchasedFor() != null && !item.getPurchasedFor().isEmpty()) {
                    String displayFor = userDisplayToUsername.entrySet()
                            .stream()
                            .filter(e -> e.getValue().equals(item.getPurchasedFor()))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(item.getPurchasedFor());
                    purchaserLabel.setText("für: " + displayFor);
                    purchaserLabel.setVisible(true);
                }

                check.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    item.setPurchased(newVal);
                    if (newVal) {
                        String username = item.getPurchasedFor() != null && !item.getPurchasedFor().isEmpty()
                                ? item.getPurchasedFor()
                                : currentUser;

                        String displayFor = userDisplayToUsername.entrySet()
                                .stream()
                                .filter(e -> e.getValue().equals(username))
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElse(username);

                        item.setPurchasedFor(username);
                        purchaserLabel.setText("für: " + displayFor);
                        purchaserLabel.setVisible(true);
                        updatePurchased(item.getId(), true, username);
                    } else {
                        item.setPurchasedFor(null);
                        purchaserLabel.setVisible(false);
                        updatePurchased(item.getId(), false, null);
                    }
                });

                VBox nameBox = new VBox(2, name, purchaserLabel);

                row.getChildren().addAll(check, qty, nameBox);
                itemBox.getChildren().add(row);
            }

            VBox wrapper = new VBox(8, catLabel, itemBox);
            listContainer.getChildren().add(wrapper);
        }
    }

    private void addItem(String itemName, String quantity, String category, String purchasedFor) {
        String sql = "INSERT INTO shopping_items (item_name, quantity, added_by, category, purchased_for) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemName);
            pstmt.setString(2, quantity);
            pstmt.setString(3, currentUser);
            pstmt.setString(4, category);
            if (purchasedFor != null) pstmt.setString(5, purchasedFor);
            else pstmt.setNull(5, Types.VARCHAR);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Hinzufügen");
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

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void clearList() {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DELETE FROM shopping_items");

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Leeren der Liste");
        }
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public Region getView() {
        return root;
    }
}