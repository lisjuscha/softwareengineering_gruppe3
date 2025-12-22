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
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;

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

    // TOTAL Labels
    private Label totalLabel;
    private Label userTotalLabel;

    // Delete-Icon (geladen einmal)
    private Image deleteIcon;

    // Split UI
    private CheckBox splitCheck;
    private FlowPane participantsPane;

    // Schulden-Visualisierung
    private ListView<String> debtsListView;

    public BudgetView(String username) {
        this.currentUser = username;
        this.transactions = FXCollections.observableArrayList();
        this.transactions.addListener((ListChangeListener<BudgetTransaction>) c -> updateTotal());

        loadDeleteIcon();
        ensureSharesTableExists();
        createView();
        loadTransactions();
    }

    private void loadDeleteIcon() {
        InputStream is = BudgetView.class.getResourceAsStream("/Löschen.png");
        if (is == null) is = BudgetView.class.getResourceAsStream("/icons/Löschen.png");
        if (is != null) {
            try { deleteIcon = new Image(is); } catch (Exception ignored) { deleteIcon = null; }
        } else deleteIcon = null;
    }

    private void createView() {
        view = new VBox(12);
        view.setPadding(new Insets(12));
        view.setMaxWidth(Double.MAX_VALUE);

        // TOTAL ganz oben (bleibt sichtbar)
        totalLabel = new Label("TOTAL: " + currencyFormat.format(0.0));
        totalLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        userTotalLabel = new Label("Ihr Saldo: " + currencyFormat.format(0.0));
        userTotalLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        VBox totalsBox = new VBox(2, totalLabel, userTotalLabel);
        totalsBox.setAlignment(Pos.CENTER_RIGHT);

        HBox totalBar = new HBox();
        totalBar.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        totalBar.getChildren().addAll(spacer, totalsBox);
        totalBar.setPadding(new Insets(0, 0, 6, 0));

        // TabPane mit 3 Tabs
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: Ausgabe hinzufügen (Form)
        Tab tabAdd = new Tab("Ausgabe hinzufügen");
        GridPane form = buildAddForm();
        ScrollPane addScroll = new ScrollPane(form);
        addScroll.setFitToWidth(true);
        addScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        tabAdd.setContent(addScroll);

        // Tab 2: Schulden & Saldo
        Tab tabDebts = new Tab("Schulden & Saldo");
        VBox debtsBox = new VBox(8);
        debtsBox.setPadding(new Insets(8));
        Label debtsLabel = new Label("Schuldenzuweisungen:");
        debtsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        debtsListView = new ListView<>();
        debtsListView.setPrefHeight(220);
        debtsListView.setFocusTraversable(false);
        debtsBox.getChildren().addAll(userTotalLabel, debtsLabel, debtsListView);
        tabDebts.setContent(debtsBox);

        // Tab 3: Kategorien
        Tab tabCats = new Tab("Kategorien");
        categoriesContainer = new VBox(12);
        categoriesContainer.setPadding(new Insets(8));
        ScrollPane catScroll = new ScrollPane(categoriesContainer);
        catScroll.setFitToWidth(true);
        catScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        tabCats.setContent(catScroll);

        tabPane.getTabs().addAll(tabAdd, tabDebts, tabCats);

        // Reihenfolge: TOTAL oben, dann Tabs
        view.getChildren().addAll(totalBar, tabPane);
    }

    private GridPane buildAddForm() {
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
        final List<String> usersInitial = loadUsernames();
        if (!usersInitial.isEmpty()) {
            personBox.getItems().addAll(usersInitial);
            if (usersInitial.contains(currentUser)) personBox.setValue(currentUser);
            else personBox.setValue(usersInitial.get(0));
        } else if (currentUser != null && !currentUser.isEmpty()) {
            personBox.getItems().add(currentUser);
            personBox.setValue(currentUser);
        }

        // Split UI
        splitCheck = new CheckBox("Aufteilen");
        participantsPane = new FlowPane();
        participantsPane.setHgap(6);
        participantsPane.setVgap(6);
        participantsPane.setPadding(new Insets(6, 0, 0, 0));
        participantsPane.setVisible(false);
        buildParticipantButtons(loadUsernames());

        splitCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            participantsPane.setVisible(newV);
            if (newV) {
                boolean any = participantsPane.getChildren().stream().filter(n -> n instanceof ToggleButton)
                        .map(n -> (ToggleButton) n).anyMatch(ToggleButton::isSelected);
                if (!any) {
                    participantsPane.getChildren().stream().filter(n -> n instanceof ToggleButton)
                            .map(n -> (ToggleButton) n).forEach(tb -> tb.setSelected(true));
                }
            }
        });

        Button addButton = new Button("Ausgabe hinzufügen");
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

            List<String> participants = new ArrayList<>();
            if (splitCheck.isSelected()) {
                participantsPane.getChildren().stream().filter(n -> n instanceof ToggleButton)
                        .map(n -> (ToggleButton) n).filter(ToggleButton::isSelected)
                        .forEach(tb -> participants.add((String) tb.getUserData()));
                if (participants.isEmpty()) {
                    showAlert("Bitte mindestens einen Beteiligten auswählen.");
                    return;
                }
            } else {
                participants.addAll(loadUsernames());
                if (participants.isEmpty()) participants.add(person);
            }

            int newId = addTransaction(beschreibung, betrag, person, datum.toString(), kategorie, participants);
            if (newId == -1) return;

            BudgetTransaction newT = new BudgetTransaction();
            newT.setId(newId > 0 ? newId : 0);
            newT.setDescription(beschreibung);
            newT.setAmount(betrag);
            newT.setDate(datum.toString());
            newT.setCategory(kategorie);
            newT.setPaidBy(person);
            double equalShare = 1.0 / participants.size();
            Map<String, Double> sharesMap = new HashMap<>();
            for (String u : participants) sharesMap.put(u, equalShare);
            newT.setShares(sharesMap);
            boolean split = sharesMap.size() > 1 || sharesMap.values().stream().anyMatch(s -> s < 0.9999);
            newT.setSplit(split);

            transactions.add(0, newT);
            rebuildCategoryTables();

            beschreibungField.clear();
            betragField.clear();
            kategorieBox.setValue(categories.get(0));
            datePicker.setValue(LocalDate.now());
            splitCheck.setSelected(false);

            List<String> refreshed = loadUsernames();
            personBox.getItems().setAll(refreshed);
            if (refreshed.contains(currentUser)) personBox.setValue(currentUser);
            else if (!refreshed.isEmpty()) personBox.setValue(refreshed.get(0));
            buildParticipantButtons(refreshed);
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
        form.add(splitCheck, 0, 3);
        form.add(participantsPane, 1, 3, 3, 1);
        form.add(addButton, 0, 4, 4, 1);
        GridPane.setMargin(addButton, new Insets(8, 0, 0, 0));
        addButton.setMaxWidth(Double.MAX_VALUE);

        return form;
    }

    private void buildParticipantButtons(List<String> users) {
        participantsPane.getChildren().clear();
        if (users == null) return;
        for (String u : users) {
            ToggleButton tb = new ToggleButton(u);
            tb.setUserData(u);
            tb.setMinHeight(28);
            tb.setStyle("-fx-background-color: transparent; -fx-border-color: #E5E7EB; -fx-border-radius: 6; -fx-background-radius: 6;");
            tb.selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV) tb.setStyle("-fx-background-color: #e6e8ff; -fx-border-color: #c7d2ff; -fx-border-radius: 6; -fx-background-radius: 6;");
                else tb.setStyle("-fx-background-color: transparent; -fx-border-color: #E5E7EB; -fx-border-radius: 6; -fx-background-radius: 6;");
            });
            participantsPane.getChildren().add(tb);
        }
    }

    /**
     * Zweiphasiges Laden: zuerst Transaktionen ohne Shares (schließt ResultSet), dann per-Transaction Shares laden.
     * Vermeidet das Öffnen einer neuen Connection während eines offenen ResultSet.
     */
    private void loadTransactions() {
        transactions.clear();

        String sql = "SELECT id, description, amount, paid_by, date, category FROM budget_transactions ORDER BY date DESC";
        List<BudgetTransaction> temp = new ArrayList<>();

        // Phase 1: nur Basisdaten (schließt ResultSet)
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                BudgetTransaction t = new BudgetTransaction();
                t.setId(rs.getInt("id"));
                t.setAmount(rs.getDouble("amount"));
                t.setDescription(rs.getString("description"));
                t.setPaidBy(rs.getString("paid_by"));
                t.setDate(rs.getString("date"));
                t.setCategory(rs.getString("category"));
                temp.add(t);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String details = e.getMessage();
            try { details += " (SQLState=" + e.getSQLState() + ", ErrorCode=" + e.getErrorCode() + ")"; } catch (Exception ignore) {}
            showAlert("Fehler beim Laden der Transaktionen: " + details);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Unbekannter Fehler beim Laden der Transaktionen: " + e.getMessage());
            return;
        }

        // Phase 2: für jede Transaktion Shares in separater Connection laden (kein offenes ResultSet mehr)
        for (BudgetTransaction t : temp) {
            Map<String, Double> shares = loadSharesForTransaction(t.getId()); // eigene Connection
            t.setShares(shares);
            boolean split = false;
            if (shares != null && !shares.isEmpty()) {
                if (shares.size() > 1) split = true;
                else {
                    for (Double v : shares.values()) {
                        if (v < 0.9999) { split = true; break; }
                    }
                }
            }
            t.setSplit(split);
            transactions.add(t);
        }

        rebuildCategoryTables();
        updateTotal();
    }

    /**
     * Lädt Shares für transactionId mit eigener Connection (schließt sie).
     */
    private Map<String, Double> loadSharesForTransaction(int transactionId) {
        Map<String, Double> result = new HashMap<>();
        String sql = "SELECT username, share FROM budget_shares WHERE transaction_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String u = rs.getString("username");
                    double s = rs.getDouble("share");
                    if (u != null) result.put(u, s);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void rebuildCategoryTables() {
        categoriesContainer.getChildren().clear();

        Map<String, List<BudgetTransaction>> grouped = new LinkedHashMap<>();
        for (String c : categories) grouped.put(c, new ArrayList<>());

        for (BudgetTransaction t : transactions) {
            String cat = t.getCategory();
            if (cat == null) cat = "Sonstiges";
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(t);
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
                    if (empty || amt == null) setText(null);
                    else setText(currencyFormat.format(amt));
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
                    private final Button btn = new Button();
                    {
                        if (deleteIcon != null) {
                            ImageView iv = new ImageView(deleteIcon);
                            iv.setFitWidth(16); iv.setFitHeight(16); btn.setGraphic(iv);
                        } else btn.setText("X");
                        btn.setOnAction(e -> {
                            BudgetTransaction t = getTableView().getItems().get(getIndex());
                            if (t != null) {
                                deleteTransaction(t.getId());
                                transactions.remove(t);
                                rebuildCategoryTables();
                            }
                        });
                    }
                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : btn);
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

    private int addTransaction(String description, double amount, String paidBy, String date, String category, List<String> participants) {
        final String sql = "INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, description);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, paidBy);
            pstmt.setString(4, date);
            pstmt.setString(5, category);

            int affected = pstmt.executeUpdate();
            if (affected == 0) { showAlert("Fehler beim Hinzufügen der Transaktion."); return -1; }

            int newId = -1;
            try (ResultSet keys = pstmt.getGeneratedKeys()) { if (keys != null && keys.next()) newId = keys.getInt(1); } catch (Exception ignored) {}

            if (newId == -1) {
                try (Statement s2 = conn.createStatement();
                     ResultSet rs = s2.executeQuery("SELECT last_insert_rowid()")) {
                    if (rs.next()) newId = rs.getInt(1);
                } catch (Exception ignored) {}
            }

            if (newId != -1 && participants != null && !participants.isEmpty()) {
                double share = 1.0 / participants.size();
                String insertShare = "INSERT INTO budget_shares (transaction_id, username, share) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertShare)) {
                    for (String u : participants) {
                        ps.setInt(1, newId);
                        ps.setString(2, u);
                        ps.setDouble(3, share);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                } catch (SQLException ex) { ex.printStackTrace(); }
            }

            return newId;
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Hinzufügen der Transaktion.");
            return -1;
        }
    }

    private void deleteTransaction(int id) {
        String delShares = "DELETE FROM budget_shares WHERE transaction_id = ?";
        String sql = "DELETE FROM budget_transactions WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(delShares)) { ps.setInt(1, id); ps.executeUpdate(); }
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) { pstmt.setInt(1, id); pstmt.executeUpdate(); }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Fehler beim Löschen der Transaktion.");
        }
    }

    private void ensureSharesTableExists() {
        String sql = "CREATE TABLE IF NOT EXISTS budget_shares (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "transaction_id INTEGER NOT NULL," +
                "username TEXT NOT NULL," +
                "share REAL NOT NULL DEFAULT 0," +
                "FOREIGN KEY (transaction_id) REFERENCES budget_transactions(id) ON DELETE CASCADE" +
                ")";
        try (Connection conn = DatabaseManager.getConnection();
             Statement s = conn.createStatement()) { s.execute(sql); }
        catch (SQLException ignored) {}
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
                if (rs.next()) return rs.getInt("is_admin") == 1;
            }
        } catch (SQLException ignored) {}
        return false;
    }

    public VBox getView() { return view; }

    private void updateTotal() {
        double globalSum = 0.0;
        for (BudgetTransaction t : transactions) {
            if (t == null) continue;
            if (!t.isSplit()) globalSum += t.getAmount();
        }
        totalLabel.setText("TOTAL: " + currencyFormat.format(globalSum));

        Map<String, Double> balances = computeBalances();
        double userBalance = 0.0;
        if (currentUser != null && balances.containsKey(currentUser)) userBalance = balances.get(currentUser);

        String formatted = currencyFormat.format(Math.abs(userBalance));
        String sign = userBalance > 0.0001 ? "+" : (userBalance < -0.0001 ? "-" : "");
        userTotalLabel.setText("Ihr Saldo: " + sign + formatted);

        List<String> assignments = computePairwiseDebts(balances);
        debtsListView.getItems().setAll(assignments);
    }

    private Map<String, Double> computeBalances() {
        Map<String, Double> bal = new HashMap<>();
        List<String> allUsers = loadUsernames();

        for (BudgetTransaction t : transactions) {
            if (t == null) continue;
            double amount = t.getAmount();
            String payer = t.getPaidBy();
            if (payer != null) bal.put(payer, bal.getOrDefault(payer, 0.0) + amount);

            Map<String, Double> shares = t.getShares();
            if (shares != null && !shares.isEmpty()) {
                for (Map.Entry<String, Double> e : shares.entrySet()) {
                    String user = e.getKey();
                    double share = e.getValue();
                    double debt = share * amount;
                    bal.put(user, bal.getOrDefault(user, 0.0) - debt);
                }
            } else {
                List<String> participants = allUsers.isEmpty() ? (payer != null ? List.of(payer) : List.of()) : allUsers;
                if (!participants.isEmpty()) {
                    double share = 1.0 / participants.size();
                    for (String u : participants) {
                        double debt = share * amount;
                        bal.put(u, bal.getOrDefault(u, 0.0) - debt);
                    }
                }
            }
        }
        return bal;
    }

    private List<String> computePairwiseDebts(Map<String, Double> balances) {
        List<String> result = new ArrayList<>();
        if (balances == null || balances.isEmpty()) return result;

        class Party { String name; double amount; Party(String n, double a){name=n;amount=a;} }

        List<Party> creditors = new ArrayList<>();
        List<Party> debtors = new ArrayList<>();

        for (Map.Entry<String, Double> e : balances.entrySet()) {
            String name = e.getKey();
            double val = e.getValue();
            if (val > 0.0001) creditors.add(new Party(name, val));
            else if (val < -0.0001) debtors.add(new Party(name, -val));
        }

        creditors.sort((a,b) -> Double.compare(b.amount, a.amount));
        debtors.sort((a,b) -> Double.compare(b.amount, a.amount));

        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            Party debt = debtors.get(i);
            Party cred = creditors.get(j);
            double take = Math.min(debt.amount, cred.amount);
            if (take > 0.0001) result.add(debt.name + " → " + cred.name + " +" + currencyFormat.format(take));
            debt.amount -= take;
            cred.amount -= take;
            if (debt.amount <= 0.0001) i++;
            if (cred.amount <= 0.0001) j++;
        }

        if (result.isEmpty()) result.add("Keine offenen Schulden");
        return result;
    }

    // Innere Model-Klasse
    public static class BudgetTransaction {
        private int id;
        private String description;
        private double amount;
        private String paidBy;
        private Integer userId;
        private String date;
        private String category;
        private Map<String, Double> shares = new HashMap<>();
        private boolean isSplit = false;

        public BudgetTransaction() {}

        // Getter/Setter
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public String getPaidBy() { return paidBy; }
        public void setPaidBy(String paidBy) { this.paidBy = paidBy; }
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Map<String, Double> getShares() { return shares; }
        public void setShares(Map<String, Double> shares) { this.shares = shares; }
        public boolean isSplit() { return isSplit; }
        public void setSplit(boolean split) { isSplit = split; }
    }
}