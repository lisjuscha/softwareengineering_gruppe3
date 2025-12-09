package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.ShoppingItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;

public class ShoppingListView {
    private BorderPane root;
    private String currentUser;
    private TableView<ShoppingItem> tableView;
    private ObservableList<ShoppingItem> items;

    public ShoppingListView(String username) {
        this.currentUser = username;
        this.items = FXCollections.observableArrayList();
        createView();
        loadItems();
    }

    private void createView() {
        root = new BorderPane();
        root.setPadding(new Insets(0));

        // Top header
        Label header = new Label("Einkaufsliste");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 26));
        header.setPadding(new Insets(12));
        BorderPane.setAlignment(header, Pos.CENTER);
        root.setTop(header);

        // Mitte: Liste mit Titel
        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(18));
        Label sectionTitle = new Label("Unsere Einkäufe");
        sectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        sectionTitle.setAlignment(Pos.CENTER);

        // Table
        tableView = new TableView<>();
        tableView.setItems(items);
        tableView.setPrefWidth(450); // schmaler
        tableView.setPrefHeight(580);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Komplett weißer Hintergrund, keine inneren Rahmen
        tableView.setStyle(
                "-fx-background-color: white;" +
                        "-fx-table-cell-border-color: transparent;" +
                        "-fx-table-header-border-color: transparent;" +
                        "-fx-selection-bar: #d0d0d0;" +
                        "-fx-selection-bar-non-focused: #d0d0d0;"
        );

        // Checkbox-Spalte
        TableColumn<ShoppingItem, Boolean> selectCol = new TableColumn<>("");
        selectCol.setCellValueFactory(param -> param.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(40);
        selectCol.setStyle("-fx-alignment: CENTER;");

        // Menge-Spalte
        TableColumn<ShoppingItem, String> quantityCol = new TableColumn<>("Menge");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        quantityCol.setPrefWidth(80);
        quantityCol.setStyle("-fx-alignment: CENTER;");

        // Artikel-Spalte
        TableColumn<ShoppingItem, String> itemCol = new TableColumn<>("Artikel");
        itemCol.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        itemCol.setPrefWidth(200);
        itemCol.setStyle("-fx-alignment: CENTER;");

        // Kategorie-Spalte
        TableColumn<ShoppingItem, String> categoryCol = new TableColumn<>("Kategorie");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(140);
        categoryCol.setStyle("-fx-alignment: CENTER;");

        // Hinzugefügt von
        TableColumn<ShoppingItem, String> addedByCol = new TableColumn<>("Hinzugefügt von");
        addedByCol.setCellValueFactory(new PropertyValueFactory<>("addedBy"));
        addedByCol.setPrefWidth(120);
        addedByCol.setStyle("-fx-alignment: CENTER;");

        tableView.getColumns().setAll(selectCol, quantityCol, itemCol, categoryCol, addedByCol);

        ScrollPane tablePane = new ScrollPane(tableView);
        tablePane.setFitToWidth(true);
        tablePane.setFitToHeight(true);
        tablePane.setStyle("-fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(tablePane, Priority.ALWAYS);

        centerBox.getChildren().addAll(sectionTitle, tablePane);
        root.setCenter(centerBox);

        // Rechts: Formular
        VBox rightBox = new VBox(12);
        rightBox.setPadding(new Insets(18));
        rightBox.setPrefWidth(500); // breiter

        Label formTitle = new Label("Neues Produkt hinzufügen");
        formTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        VBox form = new VBox(8);
        form.setPadding(new Insets(12));
        form.setStyle("-fx-border-color: #bdbdbd; -fx-border-radius: 6; -fx-background-color: white;");

        TextField itemField = new TextField();
        itemField.setPromptText("Name");

        TextField quantityField = new TextField();
        quantityField.setPromptText("Menge (z. B. 2x, 500g)");

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(
                "Milch, Eier & Käse",
                "Obst & Gemüse",
                "Brot & Gebäck",
                "Beilagen",
                "Getränke",
                "Haushalt"
        );
        categoryCombo.setValue("Milch, Eier & Käse");

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
                categoryCombo.setValue("Milch, Eier & Käse");
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

        // Gesamtstil
        root.setStyle("-fx-font-family: 'Arial'; -fx-background-color: #ffffff;");
    }

    private void loadItems() {
        items.clear();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM shopping_items ORDER BY id")) {

            while (rs.next()) {
                ShoppingItem item = new ShoppingItem(
                        rs.getInt("id"),
                        rs.getString("item_name"),
                        rs.getString("quantity"),
                        rs.getString("added_by"),
                        rs.getString("category")
                );
                items.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Laden der Artikel");
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

    private void deleteItem(int itemId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM shopping_items WHERE id = ?")) {
            pstmt.setInt(1, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Löschen");
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

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public Region getView() {
        return root;
    }
}
