package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginScreen {
    private static final Logger LOG = Logger.getLogger(LoginScreen.class.getName());

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

        Label titleLabel = new Label("WG Verwaltung");
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
        usersPane.setPrefWrapLength(800);

        view.getChildren().addAll(titleLabel, subtitleLabel, usersPane);
        view.setStyle("-fx-font-family: Arial; -fx-background-color: white;");
    }

    private void loadUsers() {
        usersPane.getChildren().clear();
        int loaded = 0;

        try (Connection conn = DatabaseManager.getConnection()) {
            boolean hasIsAdmin = hasColumn(conn, "is_admin");
            String nameCol = resolveNameColumn(conn);

            LOG.info("Loading users - nameCol=" + nameCol + ", hasIsAdmin=" + hasIsAdmin);

            String sql;
            if (hasIsAdmin) {
                sql = "SELECT " + nameCol + " AS display, COALESCE(is_admin, 0) AS is_admin FROM users ORDER BY " + nameCol;
            } else {
                sql = "SELECT " + nameCol + " AS display FROM users ORDER BY " + nameCol;
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    String display = rs.getString("display");
                    boolean isAdmin = false;
                    if (hasIsAdmin) {
                        try {
                            isAdmin = rs.getInt("is_admin") == 1;
                        } catch (SQLException ignored) {
                        }
                    }
                    Node tile = createUserTile(display, isAdmin);
                    usersPane.getChildren().add(tile);
                    loaded++;
                }
            }

        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Fehler beim Laden der Benutzer: {0}", e.getMessage());
            showAlert("Fehler beim Laden der Benutzer: " + e.getMessage());
            return;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unerwarteter Fehler beim Laden der Benutzer", e);
            showAlert("Unerwarteter Fehler: " + e.getMessage());
            return;
        }

        if (loaded == 0) {
            Button registerBtn = new Button("Neue WG erstellen");
            registerBtn.setOnAction(ev -> {
                Stage owner = com.flatmanager.App.getPrimaryStage();
                RegistrationView.showRegistration(owner, success -> {
                    if (success) {
                        loadUsers();
                    }
                });
            });
            VBox wrapper = new VBox(10, new Label("Keine Benutzer gefunden."), registerBtn);
            wrapper.setAlignment(Pos.CENTER);
            wrapper.setPadding(new Insets(20));
            usersPane.getChildren().add(wrapper);
        }
    }

    private boolean hasColumn(Connection conn, String columnName) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Prüfung auf Spalte {0} fehlgeschlagen: {1}", new Object[]{columnName, e.getMessage()});
        }
        return false;
    }

    private String resolveNameColumn(Connection conn) {
        try {
            if (hasColumn(conn, "username")) return "username";
            if (hasColumn(conn, "name")) return "name";
        } catch (Exception ignored) {
        }
        return "username";
    }

    private VBox createUserTile(String username, boolean isAdmin) {
        String displayName = username;
        String initial = displayName == null || displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase();

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

        Label nameLabel = new Label(displayName + (isAdmin ? " \u2605" : ""));
        nameLabel.setFont(Font.font("Arial", 14));
        nameLabel.setWrapText(true);
        nameLabel.setTextAlignment(TextAlignment.CENTER);
        nameLabel.setMaxWidth(120);

        VBox wrapper = new VBox(8, square, nameLabel);
        wrapper.setAlignment(Pos.CENTER);

        wrapper.setOnMouseClicked(e -> {
            LOG.fine("Clicked user: " + username + ", isAdmin=" + isAdmin);
            if (isAdmin) {
                LOG.info("Attempting admin login for: " + username);
                boolean ok = showAdminPasswordDialog(username);
                if (ok) loginAndShowDashboard(username);
            } else {
                loginAndShowDashboard(username);
            }
        });

        wrapper.setOnMouseEntered(e -> square.setStyle(
                "-fx-background-color: #d0d0d0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
        ));
        wrapper.setOnMouseExited(e -> square.setStyle(
                "-fx-background-color: #e0e0e0; -fx-border-radius: 8; -fx-background-radius: 8;"
        ));

        return wrapper;
    }

    private boolean showAdminPasswordDialog(String username) {
        LOG.info("Showing admin password dialog for: " + username);
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Admin Anmeldung");
        if (com.flatmanager.App.getPrimaryStage() != null) {
            dialog.initOwner(com.flatmanager.App.getPrimaryStage());
        } else {
            LOG.warning("PrimaryStage ist null - Dialog erhält keinen Owner.");
        }
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        PasswordField pwField = new PasswordField();
        pwField.setPromptText("Passwort");

        dialog.getDialogPane().setContent(pwField);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        pwField.textProperty().addListener((obs, oldV, newV) -> okButton.setDisable(newV == null || newV.trim().isEmpty()));

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? pwField.getText() : null);
        Optional<String> res = dialog.showAndWait();

        if (res.isPresent()) {
            String entered = res.get();
            if (authenticateAdmin(username, entered)) {
                return true;
            } else {
                showAlert("Falsches Passwort für Admin.");
                return false;
            }
        }
        return false;
    }

    private boolean authenticateAdmin(String username, String password) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE " + resolveNameColumn(conn) + " = ? COLLATE NOCASE LIMIT 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password");
                    if (stored == null) return false;
                    String hash = hashPassword(password);
                    return stored.equalsIgnoreCase(hash);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Fehler bei der Authentifizierung", e);
            showAlert("Fehler bei der Authentifizierung: " + e.getMessage());
            return false;
        }
        return false;
    }

    private void loginAndShowDashboard(String username) {
        try {
            DashboardScreen dashboard = new DashboardScreen(username);
            com.flatmanager.App.getPrimaryStage().getScene().setRoot(dashboard.getView());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Fehler beim Wechsel zum Dashboard", e);
            showAlert("Fehler beim Wechsel zum Dashboard: " + e.getMessage());
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

    // Gleiche Hash-Funktion wie beim Anlegen (SHA-256)
    private static String hashPassword(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return plain;
        }
    }
}