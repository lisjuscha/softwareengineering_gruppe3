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
        // Inhalt linksbündig und Abstand zwischen Icon und Text
        cleaningButton.setAlignment(Pos.CENTER_LEFT);
        cleaningButton.setContentDisplay(ContentDisplay.LEFT);
        cleaningButton.setGraphicTextGap(8);

        // Hand-Wash-Icon für Putzplan
        final String cleaningIconUrl = "https://img.icons8.com/ios/250/000000/wash-your-hands.png";
        try {
            Image img = new Image(cleaningIconUrl, 18, 18, true, true);
            if (!img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(18);
                iv.setFitHeight(18);
                cleaningButton.setGraphic(iv);
            }
        } catch (Exception ignored) {
            // Fallback: nur Text anzeigen
        }

        // Einkaufsliste-Button
        Button shoppingButton = new Button("Einkaufsliste");
        shoppingButton.setMaxWidth(Double.MAX_VALUE);
        shoppingButton.getStyleClass().add("nav-button");
        shoppingButton.setOnAction(e -> showShoppingLists());
        shoppingButton.setAlignment(Pos.CENTER_LEFT);
        shoppingButton.setContentDisplay(ContentDisplay.LEFT);
        shoppingButton.setGraphicTextGap(8);

        // Notizbuch-Icon für Einkaufsliste
        final String shoppingIconUrl = "https://img.icons8.com/ios/250/000000/notepad.png";
        try {
            Image img = new Image(shoppingIconUrl, 18, 18, true, true);
            if (!img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(18);
                iv.setFitHeight(18);
                shoppingButton.setGraphic(iv);
            }
        } catch (Exception ignored) {
            // Fallback: nur Text anzeigen
        }

        // Haushaltsbuch-Button
        Button budgetButton = new Button("Haushaltsbuch");
        budgetButton.setMaxWidth(Double.MAX_VALUE);
        budgetButton.getStyleClass().add("nav-button");
        budgetButton.setOnAction(e -> showHouseholdBudget());
        budgetButton.setAlignment(Pos.CENTER_LEFT);
        budgetButton.setContentDisplay(ContentDisplay.LEFT);
        budgetButton.setGraphicTextGap(8);

        final String ledgerIconUrl = "https://img.icons8.com/ios/250/000000/ledger.png";
        try {
            Image img = new Image(ledgerIconUrl, 18, 18, true, true);
            if (!img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(18);
                iv.setFitHeight(18);
                budgetButton.setGraphic(iv);
            }
        } catch (Exception ignored) {
            // Fallback: nur Text anzeigen
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