package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.InputStream;
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

    // Neues Label oben
    private Label totalLabel;

    // Delete-Icon (geladen einmal)
    private Image deleteIcon;

    public BudgetView(String username) {
        this.currentUser = username;
        this.transactions = FXCollections.observableArrayList();
        // Listener aktualisiert TOTAL automatisch bei Änderungen
        this.transactions.addListener((ListChangeListener<BudgetTransaction>) c -> updateTotal());

        loadDeleteIcon();
        createView();
        loadTransactions();
    }

    private void loadDeleteIcon() {
        // Versuche das Bild aus resources zu laden. Pfad kann angepasst werden.
        InputStream is = BudgetView.class.getResourceAsStream("/Löschen.png");
        if (is == null) {
            is = BudgetView.class.getResourceAsStream("/icons/Löschen.png");
        }
        if (is != null) {
            try {
                deleteIcon = new Image(is);
            } catch (Exception ignored) {
                deleteIcon = null;
            }
        } else {
            deleteIcon = null;
        }
    }

    private void createView() {
        view = new VBox(12);
        view.setPadding(new Insets(12));
        view.setMaxWidth(Double.MAX_VALUE);

        // TOTAL ganz oben, rechtsbündig
        totalLabel = new Label("TOTAL: " + currencyFormat.format(0.0));
        totalLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        HBox totalBar = new HBox();
        totalBar.setAlignment(Pos.CENTER_LEFT);
        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        totalBar.getChildren().addAll(totalSpacer, totalLabel);
        totalBar.setPadding(new Insets(0, 0, 6, 0));

        Label title = new Label("Haushaltsbudget");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        title.setPadding(new Insets(0, 8, 0, 0));

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 6, 0));
        topBar.setSpacing(8);
        topBar.getChildren().addAll(title);

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
        ComboBox<String> personBox = new ComboBox<>();
        personBox.setPromptText("Wähle eine Person");

        final List<String> users = loadUsernames();
        if (!users.isEmpty()) {
            personBox.getItems().addAll(users);
            if (users.contains(currentUser)) personBox.setValue(currentUser);
            else personBox.setValue(users.get(0));
        } else if (currentUser != null && !currentUser.isEmpty()) {
            personBox.getItems().add(currentUser);
            personBox.setValue(currentUser);
        }

        Button addButton = new Button("Transaktion hinzufügen");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> {
            String beschreibung = beschreibungField.getText().trim();
            String betragText = betragField.getText().trim();
            String kategorie = kategorieBox.getValue();
            LocalDate datum = datePicker.getValue();
            String person = personBox.getValue() != null ? personBox.getValue().trim() : "";

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

            int newId = addTransaction(beschreibung, betrag, person, datum.toString(), kategorie);
            if (newId == -1) {
                return;
            }

            BudgetTransaction newT = new BudgetTransaction();
            newT.setId(newId > 0 ? newId : 0);
            newT.setDescription(beschreibung);
            newT.setAmount(betrag);
            newT.setDate(datum.toString());
            newT.setCategory(kategorie);
            newT.setPaidBy(person);

            transactions.add(0, newT);
            rebuildCategoryTables();

            beschreibungField.clear();
            betragField.clear();
            kategorieBox.setValue(categories.get(0));
            datePicker.setValue(LocalDate.now());

            List<String> refreshed = loadUsernames();
            personBox.getItems().setAll(refreshed);
            if (refreshed.contains(currentUser)) personBox.setValue(currentUser);
            else if (!refreshed.isEmpty()) personBox.setValue(refreshed.get(0));
        });

        form.add(beschreibungLabel, 0, 0);
        form.add(beschreibungField, 1, 0, 3, 1);

        form.add(betragLabel, 0, 1);
        form.add(betragField, 1, 1);

        form.add(kategorieLabel, 2, 1);
        form.add(kategorieBox, 3, 1);

        form.add(datumLabel, 0, 2);
        form.add(datePicker, 1, 2);

        form.add(personLabel, 2, 2);
        form.add(personBox, 3, 2);

        form.add(addButton, 0, 3, 4, 1);
        GridPane.setMargin(addButton, new Insets(8, 0, 0, 0));
        addButton.setMaxWidth(Double.MAX_VALUE);

        categoriesContainer = new VBox(12);
        categoriesContainer.setPadding(new Insets(8));

        ScrollPane scroll = new ScrollPane(categoriesContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        // Reihenfolge: TOTAL oben, dann Titel/TopBar, Formular, Liste
        view.getChildren().addAll(totalBar, topBar, form, scroll);
    }

    private void loadTransactions() {
        transactions.clear();

        String sql = "SELECT id, description, amount, paid_by, date, category FROM budget_transactions ORDER BY date DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                BudgetTransaction t = new BudgetTransaction();
                t.setId(rs.getInt("id"));
                t.setAmount(rs.getDouble("amount"));
                t.setDescription(rs.getString("description"));
                String paidBy = rs.getString("paid_by");
                t.setPaidBy(paidBy);
                t.setDate(rs.getString("date"));
                t.setCategory(rs.getString("category"));

                transactions.add(t);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Laden der Transaktionen.");
        }

        rebuildCategoryTables();
        updateTotal();
    }

    private void rebuildCategoryTables() {
        categoriesContainer.getChildren().clear();

        Map<String, List<BudgetTransaction>> grouped = new LinkedHashMap<>();
        for (String c : categories) grouped.put(c, new ArrayList<>());

        for (BudgetTransaction t : transactions) {
            String cat = t.getCategory();
            if (cat == null) cat = "Sonstiges";
            if (grouped.containsKey(cat)) {
                grouped.get(cat).add(t);
            } else {
                grouped.computeIfAbsent("Sonstiges", k -> new ArrayList<>()).add(t);
            }
        }

        boolean adminView = isAdminUser();

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
            personCol.setPrefWidth(120);
            personCol.setCellValueFactory(cellData -> {
                BudgetTransaction t = cellData.getValue();
                String pb = t.getPaidBy();
                return new ReadOnlyStringWrapper(pb != null ? pb : "");
            });

            if (adminView) {
                TableColumn<BudgetTransaction, Void> deleteCol = new TableColumn<>("Löschen");
                deleteCol.setPrefWidth(90);
                deleteCol.setCellFactory(param -> new TableCell<>() {
                    private final Button btn;

                    {
                        // Button ohne Text, mit Icon falls vorhanden
                        btn = new Button();
                        btn.setFocusTraversable(false);
                        btn.getStyleClass().add("icon-button");
                        btn.setStyle("-fx-background-color: transparent;");

                        if (deleteIcon != null) {
                            ImageView iv = new ImageView(deleteIcon);
                            iv.setPreserveRatio(true);
                            iv.setFitWidth(16);
                            iv.setFitHeight(16);
                            btn.setGraphic(iv);
                            Tooltip.install(btn, new Tooltip("Löschen"));
                        } else {
                            // Fallback: Text und Tooltip
                            btn.setText("Löschen");
                            btn.setTooltip(new Tooltip("Löschen"));
                        }

                        btn.setOnAction(evt -> {
                            BudgetTransaction t = getTableView().getItems().get(getIndex());
                            if (t != null) {
                                int id = t.getId();
                                deleteTransaction(id);
                                transactions.removeIf(x -> x.getId() == id);
                                rebuildCategoryTables();
                                updateTotal();
                            }
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(btn);
                        }
                    }
                });
                tv.getColumns().addAll(beschrCol, betragCol, dateCol, personCol, deleteCol);
            } else {
                tv.getColumns().addAll(beschrCol, betragCol, dateCol, personCol);
            }

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

    /**
     * Einfacher Insert: schreibt description, amount, paid_by, date, category
     */
    private int addTransaction(String description, double amount, String paidBy, String date, String category) {
        final String sql = "INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, description);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, paidBy);
            pstmt.setString(4, date);
            pstmt.setString(5, category);

            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                showAlert("Fehler beim Hinzufügen der Transaktion.");
                return -1;
            }

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys != null && keys.next()) {
                    return keys.getInt(1);
                }
            } catch (Exception ignored) { }

            // Fallback für SQLite
            try (Statement s2 = conn.createStatement();
                 ResultSet rs = s2.executeQuery("SELECT last_insert_rowid()")) {
                if (rs != null && rs.next()) {
                    return rs.getInt(1);
                }
            } catch (Exception ignored) { }

            return 0;
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Hinzufügen der Transaktion.");
            return -1;
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

    private List<String> loadUsernames() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String u = rs.getString("username");
                if (u != null && !u.isEmpty()) result.add(u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            if (currentUser != null && !currentUser.isEmpty()) result.add(currentUser);
        }
        return result;
    }

    private boolean isAdminUser() {
        if (currentUser == null) return false;
        if ("admin".equalsIgnoreCase(currentUser.trim())) return true;

        String sql = "SELECT is_admin FROM users WHERE username = ? LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, currentUser.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int val = rs.getInt("is_admin");
                    return val == 1;
                }
            }
        } catch (SQLException ex) {
        }
        return false;
    }

    public VBox getView() {
        return view;
    }

    // Aktualisiert das obere TOTAL-Label
    private void updateTotal() {
        double sum = 0.0;
        for (BudgetTransaction t : transactions) {
            if (t != null) sum += t.getAmount();
        }
        totalLabel.setText("TOTAL: " + currencyFormat.format(sum));
    }

    // Innere Model-Klasse mit den fehlenden Getter/Settern
    public static class BudgetTransaction {
        private int id;
        private String description;
        private double amount;
        private String paidBy;
        private Integer userId;
        private String date;
        private String category;

        public BudgetTransaction() {}

        public BudgetTransaction(int id, String description, double amount, String paidBy, Integer userId, String date, String category) {
            this.id = id;
            this.description = description;
            this.amount = amount;
            this.paidBy = paidBy;
            this.userId = userId;
            this.date = date;
            this.category = category;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public String getPaidBy() {
            return paidBy;
        }

        public void setPaidBy(String paidBy) {
            this.paidBy = paidBy;
        }

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }
}