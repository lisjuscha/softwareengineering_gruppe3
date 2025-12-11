package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

public class LoginScreen {
    private VBox view;
    private FlowPane usersPane;

    public LoginScreen() {
        createView();
        loadUsers();
    }

    private void createView() {
        view = new VBox(20);
        view.setAlignment(Pos.TOP_CENTER);
        view.setPadding(new Insets(40));
        view.getStyleClass().add("login-container");

        Label titleLabel = new Label("Flat Manager");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        titleLabel.getStyleClass().add("title");

        Label subtitleLabel = new Label("Wähle dein Profil");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setStyle("-fx-text-fill: #666;");

        usersPane = new FlowPane();
        usersPane.setHgap(24);
        usersPane.setVgap(24);
        usersPane.setPadding(new Insets(24));
        usersPane.setAlignment(Pos.CENTER);
        usersPane.setPrefWrapLength(800); // Zeilenumbruch bei Bedarf

        view.getChildren().addAll(titleLabel, subtitleLabel, usersPane);
        view.setStyle("-fx-font-family: Arial; -fx-background-color: white;");
    }

    private void loadUsers() {
        usersPane.getChildren().clear();

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username FROM users ORDER BY username")) {

            while (rs.next()) {
                String username = rs.getString("username");
                usersPane.getChildren().add(createUserTile(username));
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Fehler beim Laden der Benutzer: " + e.getMessage());
        }
    }

    private VBox createUserTile(String username) {
        String displayName = username; // Falls es später ein display_name gibt, hier anpassen
        String initial = displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase();

        StackPane square = new StackPane();
        square.setPrefSize(120, 120);
        square.setMaxSize(120, 120);
        square.setStyle(
                "-fx-background-color: #e0e0e0;" +
                        "-fx-border-radius: 8; -fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );

        Label initialLabel = new Label(initial);
        initialLabel.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        initialLabel.setStyle("-fx-text-fill: #333;");
        square.getChildren().add(initialLabel);

        Label nameLabel = new Label(displayName);
        nameLabel.setFont(Font.font("Arial", 14));
        nameLabel.setWrapText(true);
        nameLabel.setTextAlignment(TextAlignment.CENTER);
        nameLabel.setMaxWidth(120);

        VBox wrapper = new VBox(8, square, nameLabel);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setOnMouseClicked(e -> {
            if ("admin".equalsIgnoreCase(username)) {
                showAdminPasswordDialog(username);
            } else {
                loginAndShowDashboard(username);
            }
        });

        // Hover-Effekt
        wrapper.setOnMouseEntered(e -> square.setStyle(
                "-fx-background-color: #d0d0d0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
        ));
        wrapper.setOnMouseExited(e -> square.setStyle(
                "-fx-background-color: #e0e0e0; -fx-border-radius: 8; -fx-background-radius: 8;"
        ));

        return wrapper;
    }

    private void showAdminPasswordDialog(String username) {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Admin Login");
        dlg.setHeaderText("Passwort für Admin eingeben");

        ButtonType loginBtnType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(loginBtnType, ButtonType.CANCEL);

        PasswordField pwdField = new PasswordField();
        pwdField.setPromptText("Passwort");

        VBox content = new VBox(8, new Label("Admin Passwort:"), pwdField);
        content.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(content);

        // Enable/disable Login button based on input
        Button loginBtn = (Button) dlg.getDialogPane().lookupButton(loginBtnType);
        loginBtn.setDisable(true);
        pwdField.textProperty().addListener((obs, oldV, newV) -> loginBtn.setDisable(newV.trim().isEmpty()));

        dlg.setResultConverter(btn -> {
            if (btn == loginBtnType) return pwdField.getText();
            return null;
        });

        Optional<String> result = dlg.showAndWait();
        result.ifPresent(pw -> {
            if (authenticate(username, pw)) {
                loginAndShowDashboard(username);
            } else {
                showAlert("Falsches Admin-Passwort");
            }
        });
    }

    private void loginAndShowDashboard(String username) {
        try {
            DashboardScreen dashboard = new DashboardScreen(username);
            com.flatmanager.App.getPrimaryStage().getScene().setRoot(dashboard.getView());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Fehler beim Wechsel zum Dashboard: " + e.getMessage());
        }
    }

    // WARNING: uses plain text password comparison (wie vorher)
    private boolean authenticate(String username, String password) {
        String sql = "SELECT 1 FROM users WHERE username = ? COLLATE NOCASE AND password = ? LIMIT 1";
        try (var conn = DatabaseManager.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showAlert(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    public VBox getView() {
        return view;
    }
}