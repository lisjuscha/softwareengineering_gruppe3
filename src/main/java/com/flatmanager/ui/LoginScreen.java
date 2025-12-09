package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginScreen {
    private VBox view;

    public LoginScreen() {
        createView();
    }

    private void createView() {
        view = new VBox(20);
        view.setAlignment(Pos.CENTER);
        view.setPadding(new Insets(40));
        view.getStyleClass().add("login-container");

        Label titleLabel = new Label("Flat Manager");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        titleLabel.getStyleClass().add("title");

        Label subtitleLabel = new Label("Shared Living Space Management");
        subtitleLabel.setFont(Font.font("Arial", 16));
        subtitleLabel.getStyleClass().add("subtitle");

        VBox formBox = new VBox(15);
        formBox.setAlignment(Pos.CENTER);
        formBox.setMaxWidth(350);

        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter username");
        usernameField.setMaxWidth(300);

        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        passwordField.setMaxWidth(300);

        Button loginButton = new Button("Login");
        loginButton.getStyleClass().add("primary-button");
        loginButton.setMaxWidth(300);
        loginButton.setMinHeight(40);

        Label messageLabel = new Label();
        messageLabel.getStyleClass().add("message");

        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();

            if (username.isEmpty() || password.isEmpty()) {
                messageLabel.setText("Please enter both username and password");
                messageLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            if (authenticate(username, password)) {
                messageLabel.setText("Login successful!");
                messageLabel.setStyle("-fx-text-fill: green;");
                // Show dashboard
                DashboardScreen dashboard = new DashboardScreen(username);
                com.flatmanager.App.getPrimaryStage().getScene().setRoot(dashboard.getView());
            } else {
                messageLabel.setText("Invalid username or password");
                messageLabel.setStyle("-fx-text-fill: red;");
            }
        });

        // Allow Enter key to login
        passwordField.setOnAction(e -> loginButton.fire());

        Label infoLabel = new Label("Default credentials: admin / admin");
        infoLabel.setFont(Font.font("Arial", 12));
        infoLabel.setStyle("-fx-text-fill: #666;");

        formBox.getChildren().addAll(
            usernameLabel, usernameField,
            passwordLabel, passwordField,
            loginButton, messageLabel, infoLabel
        );

        view.getChildren().addAll(titleLabel, subtitleLabel, formBox);
    }

    private boolean authenticate(String username, String password) {
        try {
            Connection conn = DatabaseManager.getConnection();
            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public VBox getView() {
        return view;
    }
}
