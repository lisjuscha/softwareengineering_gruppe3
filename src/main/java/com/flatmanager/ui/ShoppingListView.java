package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.ShoppingItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;
import java.util.*;

public class ShoppingListView {

    private BorderPane root;
    private String currentUser;
    private ObservableList<ShoppingItem> items;

    private VBox listContainer;

    public ShoppingListView(String username) {
        this.currentUser = username;
        this.items = FXCollections.observableArrayList();
        createView();
        loadItems();
    }

    private void createView() {
        root = new BorderPane();
        root.setPadding(new Insets(0));

        // Top bar: Header links, Spacer (Admin-Button zentral in DashboardScreen)
        Label header = new Label("Einkaufsliste");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 26));
        header.setPadding(new Insets(12));

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0));
        topBar.setSpacing(8);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Lokale Admin-Node entfernt
        topBar.getChildren().addAll(header, spacer);
        root.setTop(topBar);

        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(18));

        Label sectionTitle = new Label("Unsere Einkäufe");
        sectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        listContainer = new VBox(18);
        listContainer.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(listContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white; -fx-border-color: transparent;");

        centerBox.getChildren().addAll(sectionTitle, scrollPane);
        root.setCenter(centerBox);

        VBox rightBox = new VBox(12);
        rightBox.setPadding(new Insets(18));
        rightBox.setPrefWidth(380);

        Label formTitle = new Label("Neues Produkt hinzufügen");
        formTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        VBox form = new VBox(8);
        form.setPadding(new Insets(12));
        form.setStyle(
                "-fx-border-color: #bdbdbd;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-color: #ffffff;"
        );

        TextField itemField = new TextField();
        itemField.setPromptText("Name");

        TextField quantityField = new TextField();
        quantityField.setPromptText("Menge (z. B. 2x, 500g)");

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

        Button saveBtn = new Button("Speichern");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setOnAction(e -> {
            String name = itemField.getText().trim();
            String qty = quantityField.getText().trim();
            String cat = categoryCombo.getValue();

            if (!name.isEmpty()) {
                addItem(name, qty.isEmpty() ? "1" : qty, cat);
                itemField.clear();
                quantityField.clear();
                categoryCombo.setValue("Sonstiges");
                loadItems();
            } else {
                showAlert("Bitte einen Namen eingeben");
            }
        });

        Button clearBtn = new Button("Liste leeren");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setOnAction(e -> {
            clearList();
            loadItems();
        });

        form.getChildren().addAll(itemField, quantityField, categoryCombo, saveBtn);
        rightBox.getChildren().addAll(formTitle, form, clearBtn);
        root.setRight(rightBox);

        root.setStyle("-fx-font-family: Arial; -fx-background-color: white;");
    }

    private void loadItems() {
        items.clear();

        try (Connection conn = DatabaseManager.getConnection()) {
            // Sicherstellen, dass die erwartete Spalte vorhanden ist (verhindert "no such column")
            ensureColumnExists(conn, "shopping_items", "category", "TEXT", "'Sonstiges'");

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM shopping_items ORDER BY category, item_name")) {

                while (rs.next()) {
                    items.add(new ShoppingItem(
                            rs.getInt("id"),
                            rs.getString("item_name"),
                            rs.getString("quantity"),
                            rs.getString("added_by"),
                            rs.getString("category"),
                            rs.getInt("purchased") == 1
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Laden der Artikel: " + e.getMessage());
        }

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
            grouped.computeIfAbsent(item.getCategory(), c -> new ArrayList<>()).add(item);
        }

        for (String category : grouped.keySet()) {

            Label catLabel = new Label(category);
            catLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
            catLabel.setPadding(new Insets(4, 0, 4, 0));

            VBox itemBox = new VBox(6);

            for (ShoppingItem item : grouped.get(category)) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);

                CheckBox check = new CheckBox();
                check.setSelected(item.isPurchased());

                check.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    item.setPurchased(newVal);
                    updatePurchased(item.getId(), newVal);
                });

                Label qty = new Label(item.getQuantity());
                qty.setFont(Font.font(14));

                Label name = new Label(item.getItemName());
                name.setFont(Font.font(14));

                row.getChildren().addAll(check, qty, name);
                itemBox.getChildren().add(row);
            }

            VBox wrapper = new VBox(8, catLabel, itemBox);
            listContainer.getChildren().add(wrapper);
        }
    }

    private void addItem(String itemName, String quantity, String category) {
        String sql = "INSERT INTO shopping_items (item_name, quantity, added_by, category) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemName);
            pstmt.setString(2, quantity);
            pstmt.setString(3, currentUser);
            pstmt.setString(4, category);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Hinzufügen");
        }
    }

    private void updatePurchased(int id, boolean purchased) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE shopping_items SET purchased = ? WHERE id = ?"
             )) {

            pstmt.setInt(1, purchased ? 1 : 0);
            pstmt.setInt(2, id);
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