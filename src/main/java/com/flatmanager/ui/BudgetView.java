package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.BudgetTransaction;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;
import java.time.LocalDate;

public class BudgetView {
    private VBox view;
    private String currentUser;
    private TableView<BudgetTransaction> tableView;
    private ObservableList<BudgetTransaction> transactions;
    private Label totalLabel;

    public BudgetView(String username) {
        this.currentUser = username;
        this.transactions = FXCollections.observableArrayList();
        createView();
        loadTransactions();
    }

    private void createView() {
        view = new VBox(15);
        view.setPadding(new Insets(20));
        view.setMaxWidth(950);

        Label titleLabel = new Label("Household Budget");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.getStyleClass().add("section-title");

        // Add transaction form
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);

        Label descLabel = new Label("Description:");
        TextField descField = new TextField();
        descField.setPromptText("e.g., Groceries");
        descField.setPrefWidth(200);

        Label amountLabel = new Label("Amount:");
        TextField amountField = new TextField();
        amountField.setPromptText("e.g., 25.50");
        amountField.setPrefWidth(100);

        Label categoryLabel = new Label("Category:");
        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("Groceries", "Utilities", "Cleaning", "Other");
        categoryBox.setValue("Other");
        categoryBox.setPrefWidth(120);

        Label dateLabel = new Label("Date:");
        DatePicker datePicker = new DatePicker();
        datePicker.setValue(LocalDate.now());
        datePicker.setPrefWidth(150);

        Button addButton = new Button("Add Transaction");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> {
            String description = descField.getText().trim();
            String amountStr = amountField.getText().trim();
            String category = categoryBox.getValue();
            LocalDate date = datePicker.getValue();

            if (!description.isEmpty() && !amountStr.isEmpty() && date != null) {
                try {
                    double amount = Double.parseDouble(amountStr);
                    addTransaction(description, amount, category, date.toString());
                    descField.clear();
                    amountField.clear();
                    categoryBox.setValue("Other");
                    datePicker.setValue(LocalDate.now());
                    loadTransactions();
                } catch (NumberFormatException ex) {
                    showAlert("Please enter a valid amount");
                }
            } else {
                showAlert("Please fill in all fields");
            }
        });

        formGrid.add(descLabel, 0, 0);
        formGrid.add(descField, 1, 0);
        formGrid.add(amountLabel, 2, 0);
        formGrid.add(amountField, 3, 0);
        formGrid.add(categoryLabel, 0, 1);
        formGrid.add(categoryBox, 1, 1);
        formGrid.add(dateLabel, 2, 1);
        formGrid.add(datePicker, 3, 1);
        formGrid.add(addButton, 4, 1);

        // Total display
        totalLabel = new Label("Total: €0.00");
        totalLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        totalLabel.getStyleClass().add("total-label");

        // Table view
        tableView = new TableView<>();
        tableView.setItems(transactions);

        TableColumn<BudgetTransaction, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(200);

        TableColumn<BudgetTransaction, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setPrefWidth(100);
        amountCol.setCellFactory(col -> new TableCell<BudgetTransaction, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(String.format("€%.2f", amount));
                }
            }
        });

        TableColumn<BudgetTransaction, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(120);

        TableColumn<BudgetTransaction, String> paidByCol = new TableColumn<>("Paid By");
        paidByCol.setCellValueFactory(new PropertyValueFactory<>("paidBy"));
        paidByCol.setPrefWidth(120);

        TableColumn<BudgetTransaction, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(120);

        TableColumn<BudgetTransaction, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button deleteBtn = new Button("Delete");

            {
                deleteBtn.getStyleClass().add("delete-button");

                deleteBtn.setOnAction(e -> {
                    BudgetTransaction transaction = getTableView().getItems().get(getIndex());
                    deleteTransaction(transaction.getId());
                    loadTransactions();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                }
            }
        });

        tableView.getColumns().addAll(descCol, amountCol, categoryCol, paidByCol, dateCol, actionCol);

        view.getChildren().addAll(titleLabel, formGrid, totalLabel, tableView);
    }

    private void loadTransactions() {
        transactions.clear();
        double total = 0.0;

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM budget_transactions ORDER BY date DESC")) {

            while (rs.next()) {
                double amount = rs.getDouble("amount");

                // Verwende No-Arg-Konstruktor und Setter um Konstruktor-Fehler zu vermeiden
                BudgetTransaction t = new BudgetTransaction();
                t.setId(rs.getInt("id"));
                t.setAmount(amount);
                t.setDescription(rs.getString("description")); // falls null ok
                setPaidByIfPresent(t, rs.getString("paid_by"));
                t.setDate(rs.getString("date"));
                t.setCategory(rs.getString("category"));

                transactions.add(t);
                total += amount;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error loading transactions");
        }

        totalLabel.setText(String.format("Total: €%.2f", total));
    }

    private void addTransaction(String description, double amount, String category, String date) {
        String sql = "INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, description);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, currentUser);
            pstmt.setString(4, date);
            pstmt.setString(5, category);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error adding transaction");
        }
    }

    private void deleteTransaction(int transactionId) {
        String sql = "DELETE FROM budget_transactions WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, transactionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error deleting transaction");
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // --- Reflection helper für optionales paidBy-Feld/setPaidBy-Setter ---
    private void setPaidByIfPresent(BudgetTransaction t, String paidBy) {
        if (paidBy == null) return;
        try {
            Method m = t.getClass().getMethod("setPaidBy", String.class);
            m.invoke(t, paidBy);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }

        try {
            Field f = t.getClass().getDeclaredField("paidBy");
            f.setAccessible(true);
            f.set(t, paidBy);
        } catch (Exception ignored) {
        }
    }

    public VBox getView() {
        return view;
    }
}