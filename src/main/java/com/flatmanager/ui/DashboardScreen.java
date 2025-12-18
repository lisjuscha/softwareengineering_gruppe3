package com.flatmanager.ui;

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

public class DashboardScreen {
    private BorderPane view;
    private String currentUser;
    private VBox contentArea;

    public DashboardScreen(String username) {
        this.currentUser = username;
        createView();
    }

    private void createView() {
        view = new BorderPane();
        view.getStyleClass().add("dashboard");

        // Top bar
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");

        Label titleLabel = new Label("WG Verwaltung - Willkommen, " + currentUser);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        titleLabel.getStyleClass().add("dashboard-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logoutButton = new Button("Abmelden");
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(e -> logout());

        // Admin-Node erzeugen und VOR dem Logout-Button einfügen (dann steht er links vom Logout)
        Node adminNode = AdminToolbar.settingsNode(currentUser);

        topBar.getChildren().addAll(titleLabel, spacer, adminNode, logoutButton);

        // Left sidebar with navigation
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(20));
        sidebar.setMinWidth(200);
        sidebar.getStyleClass().add("sidebar");

        Label navLabel = new Label("Navigation");
        navLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        navLabel.getStyleClass().add("nav-title");

        // Putzplan-Button
        Button cleaningButton = new Button("Putzplan");
        cleaningButton.setMaxWidth(Double.MAX_VALUE);
        cleaningButton.getStyleClass().add("nav-button");
        cleaningButton.setOnAction(e -> showCleaningSchedules());
        cleaningButton.setAlignment(Pos.CENTER_LEFT);
        cleaningButton.setContentDisplay(ContentDisplay.LEFT);
        cleaningButton.setGraphicTextGap(8);

        // Lokales Icon für Putzplan: `resources/icons/Putzplan.png`
        ImageView cleaningIv = loadIconView("Putzplan.png");
        if (cleaningIv != null) {
            cleaningButton.setGraphic(cleaningIv);
        }

        // Einkaufsliste-Button
        Button shoppingButton = new Button("Einkaufsliste");
        shoppingButton.setMaxWidth(Double.MAX_VALUE);
        shoppingButton.getStyleClass().add("nav-button");
        shoppingButton.setOnAction(e -> showShoppingLists());
        shoppingButton.setAlignment(Pos.CENTER_LEFT);
        shoppingButton.setContentDisplay(ContentDisplay.LEFT);
        shoppingButton.setGraphicTextGap(8);

        // Lokales Icon für Einkaufsliste: `resources/icons/Einkaufsliste.png`
        ImageView shoppingIv = loadIconView("Einkaufsliste.png");
        if (shoppingIv != null) {
            shoppingButton.setGraphic(shoppingIv);
        }

        // Haushaltsbuch-Button
        Button budgetButton = new Button("Haushaltsbuch");
        budgetButton.setMaxWidth(Double.MAX_VALUE);
        budgetButton.getStyleClass().add("nav-button");
        budgetButton.setOnAction(e -> showHouseholdBudget());
        budgetButton.setAlignment(Pos.CENTER_LEFT);
        budgetButton.setContentDisplay(ContentDisplay.LEFT);
        budgetButton.setGraphicTextGap(8);

        // Lokales Icon für Haushaltsbuch: `resources/icons/Haushaltsbuch.png`
        ImageView budgetIv = loadIconView("Haushaltsbuch.png");
        if (budgetIv != null) {
            budgetButton.setGraphic(budgetIv);
        }

        sidebar.getChildren().addAll(navLabel, new Separator(),
                cleaningButton, shoppingButton, budgetButton);

        // Content area
        contentArea = new VBox(20);
        contentArea.setPadding(new Insets(20));
        contentArea.setAlignment(Pos.TOP_CENTER);
        contentArea.getStyleClass().add("content-area");

        showWelcome();

        view.setTop(topBar);
        view.setLeft(sidebar);
        view.setCenter(contentArea);
    }

    private ImageView loadIconView(String fileName) {
        final String[] candidates = {"/icons/" + fileName, "icons/" + fileName, "/" + fileName, fileName};
        URL url = null;
        for (String p : candidates) {
            // Versuche zuerst relative zur Klasse
            url = DashboardScreen.class.getResource(p);
            if (url != null) break;
            // Dann über ClassLoader ohne führenden '/'
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

    private void showWelcome() {
        contentArea.getChildren().clear();

        Label welcomeLabel = new Label("Willkommen bei der WG Verwaltung!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        welcomeLabel.getStyleClass().add("welcome-title");

        Label instructionLabel = new Label(
                "Verwende das Navigationsmenü links, um:\n\n" +
                        "• Putzpläne zu verwalten\n" +
                        "• Einkaufsliste(n) zu erstellen\n" +
                        "• das Haushaltsbudget zu verwalten"
        );
        instructionLabel.setFont(Font.font("Arial", 16));
        instructionLabel.getStyleClass().add("welcome-text");

        contentArea.getChildren().addAll(welcomeLabel, instructionLabel);
    }

    private void showCleaningSchedules() {
        contentArea.getChildren().clear();
        CleaningScheduleView cleaningView = new CleaningScheduleView(currentUser);
        contentArea.getChildren().add(cleaningView.getView());
    }

    private void showShoppingLists() {
        contentArea.getChildren().clear();
        ShoppingListView shoppingView = new ShoppingListView(currentUser);
        contentArea.getChildren().add(shoppingView.getView());
    }

    private void showHouseholdBudget() {
        contentArea.getChildren().clear();
        BudgetView budgetView = new BudgetView(currentUser);
        contentArea.getChildren().add(budgetView.getView());
    }

    private void logout() {
        com.flatmanager.App.showLoginScreen();
    }

    public BorderPane getView() {
        return view;
    }
}