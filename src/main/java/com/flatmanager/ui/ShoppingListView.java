package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.ShoppingItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.*;

public class ShoppingListView {
    private VBox view;
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
        view = new VBox(15);
        view.setPadding(new Insets(20));
        view.setMaxWidth(900);

        Label titleLabel = new Label("Shopping Lists");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.getStyleClass().add("section-title");

        // Add item form
        HBox formBox = new HBox(10);
        formBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextField itemField = new TextField();
        itemField.setPromptText("Item name");
        itemField.setPrefWidth(250);

        TextField quantityField = new TextField();
        quantityField.setPromptText("Quantity (e.g., 2x, 500g)");
        quantityField.setPrefWidth(150);

        Button addButton = new Button("Add Item");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> {
            String itemName = itemField.getText().trim();
            String quantity = quantityField.getText().trim();

            if (!itemName.isEmpty()) {
                addItem(itemName, quantity.isEmpty() ? "1" : quantity);
                itemField.clear();
                quantityField.clear();
                loadItems();
            } else {
                showAlert("Please enter an item name");
            }
        });

        formBox.getChildren().addAll(itemField, quantityField, addButton);

        // Table view
        tableView = new TableView<>();
        tableView.setItems(items);

        TableColumn<ShoppingItem, String> itemCol = new TableColumn<>("Item");
        itemCol.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        itemCol.setPrefWidth(300);

        TableColumn<ShoppingItem, String> quantityCol = new TableColumn<>("Quantity");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        quantityCol.setPrefWidth(120);

        TableColumn<ShoppingItem, String> addedByCol = new TableColumn<>("Added By");
        addedByCol.setCellValueFactory(new PropertyValueFactory<>("addedBy"));
        addedByCol.setPrefWidth(120);

        TableColumn<ShoppingItem, Boolean> purchasedCol = new TableColumn<>("Purchased");
        purchasedCol.setCellValueFactory(new PropertyValueFactory<>("purchased"));
        purchasedCol.setPrefWidth(100);

        TableColumn<ShoppingItem, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(180);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button purchaseBtn = new Button("Purchase");
            private final Button deleteBtn = new Button("Delete");

            {
                purchaseBtn.getStyleClass().add("complete-button");
                deleteBtn.getStyleClass().add("delete-button");

                purchaseBtn.setOnAction(e -> {
                    ShoppingItem item = getTableView().getItems().get(getIndex());
                    markItemPurchased(item.getId());
                    loadItems();
                });

                deleteBtn.setOnAction(e -> {
                    ShoppingItem item = getTableView().getItems().get(getIndex());
                    deleteItem(item.getId());
                    loadItems();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(5, purchaseBtn, deleteBtn);
                    setGraphic(buttons);
                }
            }
        });

        tableView.getColumns().addAll(itemCol, quantityCol, addedByCol, purchasedCol, actionCol);

        view.getChildren().addAll(titleLabel, formBox, tableView);
    }

    private void loadItems() {
        items.clear();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM shopping_items ORDER BY purchased, id")) {

            while (rs.next()) {
                items.add(new ShoppingItem(
                    rs.getInt("id"),
                    rs.getString("item_name"),
                    rs.getString("quantity"),
                    rs.getString("added_by"),
                    rs.getInt("purchased") == 1
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error loading items");
        }
    }

    private void addItem(String itemName, String quantity) {
        try {
            Connection conn = DatabaseManager.getConnection();
            String sql = "INSERT INTO shopping_items (item_name, quantity, added_by) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, itemName);
            pstmt.setString(2, quantity);
            pstmt.setString(3, currentUser);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error adding item");
        }
    }

    private void markItemPurchased(int itemId) {
        try {
            Connection conn = DatabaseManager.getConnection();
            String sql = "UPDATE shopping_items SET purchased = 1 WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error updating item");
        }
    }

    private void deleteItem(int itemId) {
        try {
            Connection conn = DatabaseManager.getConnection();
            String sql = "DELETE FROM shopping_items WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error deleting item");
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public VBox getView() {
        return view;
    }
}
