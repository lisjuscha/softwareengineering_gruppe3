package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.storage.Database;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.scene.Scene;

public class DashboardScreen {
    private BorderPane view;
    private String currentUser;
    private VBox contentArea;
    // timeline for automatic refresh
    private Timeline refreshTimeline;
    // active instance for cross-component notify
    private static DashboardScreen activeInstance = null;

    public DashboardScreen(String username) {
        this.currentUser = username;
        // ensure DB initialized for dependent views
        try {
            Database.init();
        } catch (Exception ignored) {
        }
        createView();
    }

    private void createView() {
        view = new BorderPane();
        // store a reference to this controller on the root node so other code can find it as fallback
        view.setUserData(this);
        view.getStyleClass().add("dashboard");

        // Top bar
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");

        Label titleLabel = new Label("Unser Haushalt");
        // styling via CSS: unified heading size and weight
        titleLabel.getStyleClass().addAll("dashboard-title", "title");
        titleLabel.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Theme toggle button (sun / moon)
        Button themeToggle = new Button();
        themeToggle.setWrapText(true);
        themeToggle.setMaxWidth(Double.MAX_VALUE);
        themeToggle.getStyleClass().addAll("icon-button", "theme-toggle");
        // initial icon
        String icon = com.flatmanager.ui.ThemeManager.isDark() ? "🌙" : "☀";
        themeToggle.setText(icon);
        themeToggle.setOnAction(e -> {
            com.flatmanager.ui.ThemeManager.toggle();
            // update icon after toggle
            themeToggle.setText(com.flatmanager.ui.ThemeManager.isDark() ? "🌙" : "☀");
        });

        Button logoutButton = new Button("Abmelden");
        logoutButton.setWrapText(true);
        logoutButton.setMaxWidth(Double.MAX_VALUE);
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(e -> logout());

        Node adminNode = AdminToolbar.settingsNode(currentUser);

        topBar.getChildren().addAll(titleLabel, spacer, adminNode, themeToggle, logoutButton);

        // Content area: stack of cards (default center)
        contentArea = new VBox(18);
        contentArea.setPadding(new Insets(18));
        contentArea.setAlignment(Pos.TOP_CENTER);

        // Create three summary cards and add as default dashboard center
        TitledPane tasksCard = createTasksCard();
        TitledPane shoppingCard = createShoppingCard();
        TitledPane financeCard = createFinanceCard();
        tasksCard.setCollapsible(false);
        shoppingCard.setCollapsible(false);
        financeCard.setCollapsible(false);
        contentArea.getChildren().addAll(tasksCard, shoppingCard, financeCard);

        // linke Navigationsleiste
        VBox sidebar = createSidebar();
        view.setLeft(sidebar);

        view.setTop(topBar);
        view.setCenter(contentArea);

        // refresh values when shown / could add listeners to stage size if needed
        refreshAll();

        // create a periodic refresh timeline (every 5 seconds)
        if (refreshTimeline == null) {
            refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), ev -> {
                // only refresh when dashboard content is currently visible in center
                Platform.runLater(() -> {
                    if (view.getCenter() == contentArea) refreshAll();
                });
            }));
            refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        }

        // start/stop timeline based on scene visibility
        view.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                // scene set, start timeline
                if (refreshTimeline != null) refreshTimeline.play();
                // register as active instance
                DashboardScreen.registerActive(DashboardScreen.this);
            } else {
                // removed from scene, stop timeline to avoid leaks
                if (refreshTimeline != null) refreshTimeline.stop();
                // unregister
                DashboardScreen.unregisterActive(DashboardScreen.this);
            }
        });

        // Also pause if center is changed away from dashboard, resume when returned
        view.centerProperty().addListener((obs, oldC, newC) -> {
            if (refreshTimeline == null) return;
            if (newC == contentArea) refreshTimeline.play();
            else refreshTimeline.pause();
        });
    }

    // Static helpers so other views can trigger an immediate dashboard refresh
    private static synchronized void registerActive(DashboardScreen instance) {
        activeInstance = instance;
    }

    private static synchronized void unregisterActive(DashboardScreen instance) {
        if (activeInstance == instance) activeInstance = null;
    }

    public static void notifyRefreshNow() {
        DashboardScreen inst = activeInstance;
        if (inst == null) {
            // fallback: try to find a dashboard instance from the primary stage scene
            try {
                if (com.flatmanager.App.getPrimaryStage() != null && com.flatmanager.App.getPrimaryStage().getScene() != null) {
                    Scene s = com.flatmanager.App.getPrimaryStage().getScene();
                    for (Node n : s.getRoot().lookupAll(".dashboard")) {
                        Object ud = n.getUserData();
                        if (ud instanceof DashboardScreen) {
                            DashboardScreen found = (DashboardScreen) ud;
                            Platform.runLater(found::refreshAll);
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}
            return;
        }
        Platform.runLater(() -> inst.refreshAll());
    }

    private TitledPane createTasksCard() {
        // Card header with icon and title
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER);
        // Use expanding spacers so the title stays centered regardless of width
        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);
        ImageView iv = loadIconView("Putzplan_icon.png");
        if (iv != null) iv.setFitWidth(22);
        Label lbl = new Label("Aufgaben");
        lbl.getStyleClass().add("title");
        HBox content = new HBox(8, iv, lbl);
        content.setAlignment(Pos.CENTER);
        header.getChildren().addAll(leftSpacer, content, rightSpacer);

        // content: two boxes side by side
        HBox boxes = new HBox(12);
        boxes.setPadding(new Insets(12));
        boxes.setAlignment(Pos.CENTER);

        VBox myTasksBox = createStatBox("Deine Aufgaben", "0");
        VBox openTasksBox = createStatBox("Offene Aufgaben", "0");

        HBox.setHgrow(myTasksBox, Priority.ALWAYS);
        HBox.setHgrow(openTasksBox, Priority.ALWAYS);
        boxes.getChildren().addAll(myTasksBox, openTasksBox);

        TitledPane pane = new TitledPane();
        pane.setGraphic(header);
        pane.setContent(boxes);
        pane.setPadding(new Insets(0));
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setPrefWidth(720);
        pane.setUserData(new VBox[]{myTasksBox, openTasksBox}); // store references
        return pane;
    }

    private TitledPane createShoppingCard() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER);
        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);
        ImageView iv = loadIconView("Einkaufsliste_icon.png");
        if (iv != null) iv.setFitWidth(22);
        Label lbl = new Label("Einkaufsliste");
        lbl.getStyleClass().add("title");
        HBox content = new HBox(8, iv, lbl);
        content.setAlignment(Pos.CENTER);
        header.getChildren().addAll(leftSpacer, content, rightSpacer);

        HBox boxes = new HBox(12);
        boxes.setPadding(new Insets(12));
        boxes.setAlignment(Pos.CENTER);

        VBox totalBox = createStatBox("Einträge gesamt", "0");
        VBox mineBox = createStatBox("Für dich", "0");

        HBox.setHgrow(totalBox, Priority.ALWAYS);
        HBox.setHgrow(mineBox, Priority.ALWAYS);
        boxes.getChildren().addAll(totalBox, mineBox);

        TitledPane pane = new TitledPane();
        pane.setGraphic(header);
        pane.setContent(boxes);
        pane.setCollapsible(false);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setPrefWidth(720);
        pane.setUserData(new VBox[]{totalBox, mineBox});
        return pane;
    }

    private TitledPane createFinanceCard() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER);
        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);
        ImageView iv = loadIconView("Haushaltsbuch_icon.png");
        if (iv != null) iv.setFitWidth(22);
        Label lbl = new Label("Finanzen");
        lbl.getStyleClass().add("title");
        HBox content = new HBox(8, iv, lbl);
        content.setAlignment(Pos.CENTER);
        header.getChildren().addAll(leftSpacer, content, rightSpacer);

        HBox boxes = new HBox(12);
        boxes.setPadding(new Insets(12));
        boxes.setAlignment(Pos.CENTER);

        VBox owedToMe = createStatBox("Du bekommst zurück", "0,00 €");
        VBox oweOthers = createStatBox("Du musst abgeben", "0,00 €");

        HBox.setHgrow(owedToMe, Priority.ALWAYS);
        HBox.setHgrow(oweOthers, Priority.ALWAYS);
        boxes.getChildren().addAll(owedToMe, oweOthers);

        TitledPane pane = new TitledPane();
        pane.setGraphic(header);
        pane.setContent(boxes);
        pane.setCollapsible(false);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setPrefWidth(720);
        pane.setUserData(new VBox[]{owedToMe, oweOthers});
        return pane;
    }

    private VBox createStatBox(String labelText, String valueText) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("card");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(320);

        Label value = new Label(valueText);
        value.getStyleClass().add("title");
        value.setWrapText(true);
        value.setId("stat-value");

        Label label = new Label(labelText);
        label.getStyleClass().add("small-text");
        label.setWrapText(true);

        box.getChildren().addAll(value, label);
        return box;
    }

    private void refreshAll() {
        refreshTasks();
        refreshShopping();
        refreshFinance();
    }

    private void refreshTasks() {
        try (Connection conn = DatabaseManager.getConnection()) {
            // count tasks assigned to currentUser
            int myCount = 0;
            int openCount = 0;

            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM cleaning_tasks WHERE assigned_to = ?")) {
                ps.setString(1, currentUser);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) myCount = rs.getInt("c");
                }
            } catch (Exception ignored) {
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM cleaning_tasks WHERE completed = 0")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) openCount = rs.getInt("c");
                }
            } catch (Exception ignored) {
            }

            // update UI: find the titled pane by its header label text safely
            for (Node n : contentArea.getChildren()) {
                if (n instanceof TitledPane) {
                    TitledPane tp = (TitledPane) n;
                    String header = getCardHeaderText(tp);
                    if ("Aufgaben".equals(header)) {
                        VBox[] boxes = (VBox[]) tp.getUserData();
                        Node v0 = boxes[0].lookup("#stat-value");
                        Node v1 = boxes[1].lookup("#stat-value");
                        if (v0 instanceof Label) ((Label) v0).setText(String.valueOf(myCount));
                        if (v1 instanceof Label) ((Label) v1).setText(String.valueOf(openCount));
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Fehler beim Laden der Aufgaben-Daten: " + e.getMessage());
        }
    }

    private void refreshShopping() {
        try (Connection conn = DatabaseManager.getConnection()) {
            int total = 0;
            int mine = 0;

            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM shopping_items")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) total = rs.getInt("c");
                }
            } catch (Exception ignored) {
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM shopping_items WHERE purchased_for = ?")) {
                ps.setString(1, currentUser);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) mine = rs.getInt("c");
                }
            } catch (Exception ignored) {
            }

            for (Node n : contentArea.getChildren()) {
                if (n instanceof TitledPane) {
                    TitledPane tp = (TitledPane) n;
                    String header = getCardHeaderText(tp);
                    if ("Einkaufsliste".equals(header)) {
                        VBox[] boxes = (VBox[]) tp.getUserData();
                        Node v0 = boxes[0].lookup("#stat-value");
                        Node v1 = boxes[1].lookup("#stat-value");
                        if (v0 instanceof Label) ((Label) v0).setText(String.valueOf(total));
                        if (v1 instanceof Label) ((Label) v1).setText(String.valueOf(mine));
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Fehler beim Laden der Einkaufs-Daten: " + e.getMessage());
        }
    }

    private void refreshFinance() {
        // We'll compute per-user balances from transactions and shares similarly to BudgetView
        try (Connection conn = DatabaseManager.getConnection()) {
            // resolve currentUser to actual username stored in users table (handles display names)
            String resolvedUser = resolveUsername(conn, currentUser);
            // Load all transactions
            String txSql = "SELECT id, amount, paid_by FROM budget_transactions";
            List<TransactionRow> txs = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(txSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    double amount = rs.getDouble("amount");
                    String paidBy = rs.getString("paid_by");
                    txs.add(new TransactionRow(id, amount, paidBy));
                }
            }

            // Load shares for transactions
            Map<Integer, Map<String, Double>> sharesMap = new HashMap<>();
            String shareSql = "SELECT transaction_id, username, share FROM budget_shares";
            try (PreparedStatement ps = conn.prepareStatement(shareSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int tid = rs.getInt("transaction_id");
                    String user = rs.getString("username");
                    double share = rs.getDouble("share");
                    sharesMap.computeIfAbsent(tid, k -> new HashMap<>()).put(user, share);
                }
            }

            // Load all users for equal-split fallback
            List<String> users = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM users");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) users.add(rs.getString("username"));
            }

            // compute balances map
            Map<String, Double> balances = new HashMap<>();
            for (TransactionRow tr : txs) {
                if (tr.paidBy != null) balances.put(tr.paidBy, balances.getOrDefault(tr.paidBy, 0.0) + tr.amount);
                Map<String, Double> shares = sharesMap.get(tr.id);
                if (shares != null && !shares.isEmpty()) {
                    for (Map.Entry<String, Double> e : shares.entrySet()) {
                        String u = e.getKey();
                        double debt = e.getValue() * tr.amount;
                        balances.put(u, balances.getOrDefault(u, 0.0) - debt);
                    }
                } else {
                    List<String> participants = users.isEmpty() ? (tr.paidBy != null ? List.of(tr.paidBy) : List.of()) : users;
                    if (!participants.isEmpty()) {
                        double share = 1.0 / participants.size();
                        for (String u : participants) {
                            double debt = share * tr.amount;
                            balances.put(u, balances.getOrDefault(u, 0.0) - debt);
                        }
                    }
                }
            }

            double owedToMe = 0.0;
            double oweOthers = 0.0;
            if (resolvedUser != null) {
                double bal = balances.getOrDefault(resolvedUser, 0.0);
                // as fallback, try case-insensitive match if exact key not found
                if (Math.abs(bal) < 0.000001) {
                    for (Map.Entry<String, Double> e : balances.entrySet()) {
                        if (e.getKey() != null && resolvedUser.equalsIgnoreCase(e.getKey())) {
                            bal = e.getValue();
                            break;
                        }
                    }
                }
                if (bal > 0) owedToMe = bal;
                else oweOthers = -bal;
            } else if (currentUser != null) {
                // last attempt: use raw currentUser as key (case-insensitive lookup)
                double bal = balances.getOrDefault(currentUser, 0.0);
                if (Math.abs(bal) < 0.000001) {
                    for (Map.Entry<String, Double> e : balances.entrySet()) {
                        if (e.getKey() != null && currentUser.equalsIgnoreCase(e.getKey())) {
                            bal = e.getValue();
                            break;
                        }
                    }
                }
                if (bal > 0) owedToMe = bal;
                else oweOthers = -bal;
            }

            for (Node n : contentArea.getChildren()) {
                if (n instanceof TitledPane) {
                    TitledPane tp = (TitledPane) n;
                    String header = getCardHeaderText(tp);
                    if ("Finanzen".equals(header)) {
                        VBox[] boxes = (VBox[]) tp.getUserData();
                        Node v0 = boxes[0].lookup("#stat-value");
                        Node v1 = boxes[1].lookup("#stat-value");
                        if (v0 instanceof Label) ((Label) v0).setText(String.format("%.2f €", owedToMe));
                        if (v1 instanceof Label) ((Label) v1).setText(String.format("%.2f €", oweOthers));
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Fehler beim Laden der Finanz-Daten: " + e.getMessage());
        }
    }

    // try to resolve display username to actual username from users table
    private String resolveUsername(Connection conn, String user) {
        if (conn == null || user == null) return null;
        String trimmed = user.trim();
        // if format like "Name (username)" extract inside parentheses
        int p = trimmed.indexOf('(');
        if (p >= 0) {
            int q = trimmed.indexOf(')', p);
            if (q > p) {
                String inside = trimmed.substring(p + 1, q).trim();
                if (!inside.isEmpty()) return inside;
            }
        }
        // try exact match on username or name (case-insensitive)
        try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM users WHERE username = ? OR name = ? COLLATE NOCASE LIMIT 1")) {
            ps.setString(1, trimmed);
            ps.setString(2, trimmed);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("username");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ImageView loadIconView(String fileName) {
        final String[] candidates = {"/icons/" + fileName, "icons/" + fileName, "/" + fileName, fileName};
        URL url = null;
        for (String p : candidates) {
            url = DashboardScreen.class.getResource(p);
            if (url != null) break;
            String lookup = p.startsWith("/") ? p.substring(1) : p;
            url = DashboardScreen.class.getClassLoader().getResource(lookup);
            if (url != null) break;
        }

        if (url != null) {
            try {
                Image img = new Image(url.toExternalForm(), 18, 18, true, true, true);
                if (!img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(18);
                    iv.setFitHeight(18);
                    return iv;
                }
            } catch (Exception ex) {
                System.err.println("Fehler beim Laden des Icons `" + fileName + "`: " + ex);
            }
        } else {
            System.err.println("Icon nicht gefunden im Klassenpfad: versuchte Pfade für `" + fileName + "`");
        }
        return null;
    }

    private void logout() {
        com.flatmanager.App.showLoginScreen();
    }

    private VBox createSidebar() {
        VBox sb = new VBox(8);
        sb.getStyleClass().add("sidebar");
        sb.setPadding(new Insets(12));
        sb.setPrefWidth(160);
        sb.setAlignment(Pos.TOP_LEFT); // linksbündig für die Sidebar-Kinder

        // Dashboard button with icon (changed to Dashboard_icon.png)
        Button dashBtn = new Button("Dashboard");
        dashBtn.setMaxWidth(Double.MAX_VALUE);
        dashBtn.setAlignment(Pos.CENTER_LEFT);
        dashBtn.setStyle("-fx-alignment: CENTER_LEFT;"); // Fallback falls setAlignment nicht greift
        ImageView dashIv = loadIconView("Dashboard_icon.png");
        if (dashIv != null) {
            dashIv.setFitWidth(18);
            dashIv.setFitHeight(18);
            dashBtn.setGraphic(dashIv);
            dashBtn.setContentDisplay(ContentDisplay.LEFT);
            dashBtn.setGraphicTextGap(8);
            dashBtn.getStyleClass().add("icon-button");
        }
        dashBtn.setOnAction(e -> view.setCenter(contentArea));

        // Putzplan button
        Button cleaningBtn = new Button("Putzplan");
        cleaningBtn.setMaxWidth(Double.MAX_VALUE);
        cleaningBtn.setAlignment(Pos.CENTER_LEFT);
        cleaningBtn.setStyle("-fx-alignment: CENTER_LEFT;");
        ImageView cleanIv = loadIconView("Putzplan_icon.png");
        if (cleanIv != null) {
            cleanIv.setFitWidth(18);
            cleanIv.setFitHeight(18);
            cleaningBtn.setGraphic(cleanIv);
            cleaningBtn.setContentDisplay(ContentDisplay.LEFT);
            cleaningBtn.setGraphicTextGap(8);
            cleaningBtn.getStyleClass().add("icon-button");
        }
        cleaningBtn.setOnAction(e -> {
            try {
                CleaningScheduleView csv = new CleaningScheduleView(currentUser);
                view.setCenter(csv.getView());
            } catch (Exception ex) {
                showError("Fehler beim Öffnen des Putzplans: " + ex.getMessage());
            }
        });

        // Einkaufsliste button
        Button shoppingBtn = new Button("Einkaufsliste");
        shoppingBtn.setMaxWidth(Double.MAX_VALUE);
        shoppingBtn.setAlignment(Pos.CENTER_LEFT);
        shoppingBtn.setStyle("-fx-alignment: CENTER_LEFT;");
        ImageView shopIv = loadIconView("Einkaufsliste_icon.png");
        if (shopIv != null) {
            shopIv.setFitWidth(18);
            shopIv.setFitHeight(18);
            shoppingBtn.setGraphic(shopIv);
            shoppingBtn.setContentDisplay(ContentDisplay.LEFT);
            shoppingBtn.setGraphicTextGap(8);
            shoppingBtn.getStyleClass().add("icon-button");
        }
        shoppingBtn.setOnAction(e -> {
            try {
                ShoppingListView slv = new ShoppingListView(currentUser);
                view.setCenter(slv.getView());
            } catch (Exception ex) {
                showError("Fehler beim Öffnen der Einkaufsliste: " + ex.getMessage());
            }
        });

        // Haushaltsbuch button
        Button budgetBtn = new Button("Haushaltsbuch");
        budgetBtn.setMaxWidth(Double.MAX_VALUE);
        budgetBtn.setAlignment(Pos.CENTER_LEFT);
        budgetBtn.setStyle("-fx-alignment: CENTER_LEFT;");
        ImageView budIv = loadIconView("Haushaltsbuch_icon.png");
        if (budIv != null) {
            budIv.setFitWidth(18);
            budIv.setFitHeight(18);
            budgetBtn.setGraphic(budIv);
            budgetBtn.setContentDisplay(ContentDisplay.LEFT);
            budgetBtn.setGraphicTextGap(8);
            budgetBtn.getStyleClass().add("icon-button");
        }
        budgetBtn.setOnAction(e -> {
            try {
                BudgetView bv = new BudgetView(currentUser);
                view.setCenter(bv.getView());
            } catch (Exception ex) {
                showError("Fehler beim Öffnen des Haushaltsbuchs: " + ex.getMessage());
            }
        });

        sb.getChildren().addAll(dashBtn, cleaningBtn, shoppingBtn, budgetBtn);
        return sb;
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setContentText(message);
        com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
        if (com.flatmanager.App.getPrimaryStage() != null) a.initOwner(com.flatmanager.App.getPrimaryStage());
        a.showAndWait();
    }

    public BorderPane getView() {
        return view;
    }

    // Helper: extract header text from a TitledPane graphic that may contain nested HBoxes
    private String getCardHeaderText(TitledPane tp) {
        if (tp == null) return null;
        Node graphic = tp.getGraphic();
        if (graphic == null) return null;
        // If graphic is HBox, search its children for a Label (or nested HBox containing Label)
        if (graphic instanceof HBox) {
            for (Node child : ((HBox) graphic).getChildren()) {
                if (child instanceof Label) return ((Label) child).getText();
                if (child instanceof HBox) {
                    for (Node inner : ((HBox) child).getChildren()) {
                        if (inner instanceof Label) return ((Label) inner).getText();
                    }
                }
            }
        } else if (graphic instanceof Label) {
            return ((Label) graphic).getText();
        }
        return null;
    }

    private static class TransactionRow {
        final int id;
        final double amount;
        final String paidBy;

        TransactionRow(int id, double amount, String paidBy) {
            this.id = id;
            this.amount = amount;
            this.paidBy = paidBy;
        }
    }
}



