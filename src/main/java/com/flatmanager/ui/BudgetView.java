package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.BudgetTransaction;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

public class BudgetView {
    private VBox view;
    private String currentUser;
    private ObservableList<BudgetTransaction> transactions;

    private final List<String> categories = List.of("Einkäufe", "Haushalt", "Abos", "Aktivitäten", "Sonstiges");
    private VBox categoriesContainer;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY);

    public BudgetView(String username) {
        this.currentUser = username;
        this.transactions = FXCollections.observableArrayList();
        createView();
        loadTransactions();
    }

    private void createView() {
        view = new VBox(16);
        view.setPadding(new Insets(18));
        view.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("Haushaltsbudget");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 35));

        // --- Formular zum Hinzufügen ---
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(6));

        Label beschreibungLabel = new Label("Beschreibung:");
        TextField beschreibungField = new TextField();
        beschreibungField.setPromptText("z. B. Einkäufe");

        Label betragLabel = new Label("Betrag:");
        TextField betragField = new TextField();
        betragField.setPromptText("z. B. 12.50");

        Label kategorieLabel = new Label("Kategorie:");
        ComboBox<String> kategorieBox = new ComboBox<>();
        kategorieBox.getItems().addAll(categories);
        kategorieBox.setValue(categories.get(0));

        Label datumLabel = new Label("Datum:");
        DatePicker datePicker = new DatePicker(LocalDate.now());

        Label personLabel = new Label("Person:");
        TextField personField = new TextField();
        personField.setPromptText("z. B. Lisa");

        Button addButton = new Button("Transaktion hinzufügen");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> {
            String beschreibung = beschreibungField.getText().trim();
            String betragText = betragField.getText().trim();
            String kategorie = kategorieBox.getValue();
            LocalDate datum = datePicker.getValue();
            String person = personField.getText().trim();

            if (beschreibung.isEmpty() || betragText.isEmpty() || datum == null || person.isEmpty()) {
                showAlert("Bitte alle Felder ausfüllen.");
                return;
            }

            double betrag;
            try {
                betrag = Double.parseDouble(betragText.replace(",", "."));
            } catch (NumberFormatException ex) {
                showAlert("Bitte einen gültigen Betrag eingeben (z. B. 12.50).");
                return;
            }

            addTransaction(beschreibung, betrag, kategorie, datum.toString(), person);

            beschreibungField.clear();
            betragField.clear();
            kategorieBox.setValue(categories.get(0));
            datePicker.setValue(LocalDate.now());
            personField.clear();

            loadTransactions();
        });

        // Layout des Formulars
        form.add(beschreibungLabel, 0, 0);
        form.add(beschreibungField, 1, 0, 3, 1);

        form.add(betragLabel, 0, 1);
        form.add(betragField, 1, 1);

        form.add(kategorieLabel, 2, 1);
        form.add(kategorieBox, 3, 1);

        form.add(datumLabel, 0, 2);
        form.add(datePicker, 1, 2);

        form.add(personLabel, 2, 2);
        form.add(personField, 3, 2);

        form.add(addButton, 0, 3, 4, 1);
        GridPane.setMargin(addButton, new Insets(8, 0, 0, 0));
        addButton.setMaxWidth(Double.MAX_VALUE);

        // --- Container für Kategorien/Tables ---
        categoriesContainer = new VBox(18);
        categoriesContainer.setPadding(new Insets(8));

        ScrollPane scroll = new ScrollPane(categoriesContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        view.getChildren().addAll(title, form, scroll);
    }

    private void loadTransactions() {
        transactions.clear();

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM budget_transactions ORDER BY date DESC")) {

            while (rs.next()) {
                double amount = rs.getDouble("amount");

                BudgetTransaction t = new BudgetTransaction();
                t.setId(rs.getInt("id"));
                t.setAmount(amount);
                t.setDescription(rs.getString("description"));
                setPaidByIfPresent(t, rs.getString("paid_by"));
                t.setDate(rs.getString("date"));
                t.setCategory(rs.getString("category"));

                transactions.add(t);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Laden der Transaktionen.");
        }

        rebuildCategoryTables();
    }

    private void rebuildCategoryTables() {
        categoriesContainer.getChildren().clear();

        Map<String, List<BudgetTransaction>> grouped = new LinkedHashMap<>();
        for (String c : categories) grouped.put(c, new ArrayList<>());

        for (BudgetTransaction t : transactions) {
            String cat = t.getCategory();
            if (grouped.containsKey(cat)) {
                grouped.get(cat).add(t);
            } else {
                grouped.computeIfAbsent("Sonstiges", k -> new ArrayList<>()).add(t);
            }
        }

        for (String category : grouped.keySet()) {
            List<BudgetTransaction> list = grouped.get(category);
            if (list.isEmpty()) {
                Label catLabel = new Label(category);
                catLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
                Label empty = new Label("Keine Einträge");
                empty.setPadding(new Insets(4, 0, 8, 6));
                VBox box = new VBox(6, catLabel, empty);
                categoriesContainer.getChildren().add(box);
                continue;
            }

            Label catLabel = new Label(category);
            catLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

            TableView<BudgetTransaction> tv = new TableView<>();
            tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            tv.setPrefHeight(Math.min(200, 40 + list.size() * 28));

            TableColumn<BudgetTransaction, String> beschrCol = new TableColumn<>("Beschreibung");
            beschrCol.setCellValueFactory(new PropertyValueFactory<>("description"));
            beschrCol.setPrefWidth(300);

            TableColumn<BudgetTransaction, Double> betragCol = new TableColumn<>("Betrag");
            betragCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
            betragCol.setPrefWidth(100);
            betragCol.setCellFactory(col -> new TableCell<BudgetTransaction, Double>() {
                @Override
                protected void updateItem(Double amt, boolean empty) {
                    super.updateItem(amt, empty);
                    if (empty || amt == null) {
                        setText(null);
                    } else {
                        setText(currencyFormat.format(amt));
                    }
                    setAlignment(Pos.CENTER_RIGHT);
                }
            });

            TableColumn<BudgetTransaction, String> dateCol = new TableColumn<>("Datum");
            dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
            dateCol.setPrefWidth(120);

            TableColumn<BudgetTransaction, String> personCol = new TableColumn<>("Person");
            personCol.setCellValueFactory(new PropertyValueFactory<>("paidBy"));
            personCol.setPrefWidth(120);

            TableColumn<BudgetTransaction, Void> deleteCol = new TableColumn<>("Löschen");
            deleteCol.setPrefWidth(90);
            deleteCol.setCellFactory(param -> new TableCell<>() {
                private final Button delBtn = new Button("Löschen");

                {
                    delBtn.setOnAction(e -> {
                        BudgetTransaction bt = getTableView().getItems().get(getIndex());
                        deleteTransaction(bt.getId());
                        loadTransactions();
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : delBtn);
                }
            });

            tv.getColumns().addAll(beschrCol, betragCol, dateCol, personCol, deleteCol);
            tv.getItems().addAll(list);

            double sum = 0.0;
            for (BudgetTransaction t : list) sum += t.getAmount();
            Label sumLabel = new Label("Summe " + category + ": " + currencyFormat.format(sum));
            sumLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            sumLabel.setPadding(new Insets(6, 0, 0, 0));

            VBox box = new VBox(8, catLabel, tv, sumLabel);
            box.setPadding(new Insets(6, 0, 12, 0));
            box.setStyle("-fx-background-color: #ffffff; -fx-border-color: transparent;");
            categoriesContainer.getChildren().add(box);
        }
    }

    private void addTransaction(String description, double amount, String category, String date, String paidBy) {
        String sql = "INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, description);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, paidBy);
            pstmt.setString(4, date);
            pstmt.setString(5, category);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Hinzufügen der Transaktion.");
        }
    }

    private void deleteTransaction(int id) {
        String sql = "DELETE FROM budget_transactions WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Löschen der Transaktion.");
        }
    }

    private void showAlert(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

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