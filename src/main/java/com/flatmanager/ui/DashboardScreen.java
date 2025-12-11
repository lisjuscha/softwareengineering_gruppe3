package com.flatmanager.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
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

        Label titleLabel = new Label("Flat Manager - Welcome, " + currentUser);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        titleLabel.getStyleClass().add("dashboard-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logoutButton = new Button("Logout");
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

        Button cleaningButton = new Button("Cleaning Schedules");
        cleaningButton.setMaxWidth(Double.MAX_VALUE);
        cleaningButton.getStyleClass().add("nav-button");
        cleaningButton.setOnAction(e -> showCleaningSchedules());

        Button shoppingButton = new Button("Shopping Lists");
        shoppingButton.setMaxWidth(Double.MAX_VALUE);
        shoppingButton.getStyleClass().add("nav-button");
        shoppingButton.setOnAction(e -> showShoppingLists());

        Button budgetButton = new Button("Household Budget");
        budgetButton.setMaxWidth(Double.MAX_VALUE);
        budgetButton.getStyleClass().add("nav-button");
        budgetButton.setOnAction(e -> showHouseholdBudget());

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

        Label welcomeLabel = new Label("Welcome to Flat Manager!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        welcomeLabel.getStyleClass().add("welcome-title");

        Label instructionLabel = new Label(
                "Use the navigation menu on the left to:\n\n" +
                        "• Manage Cleaning Schedules\n" +
                        "• Create Shopping Lists\n" +
                        "• Track Household Budget"
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