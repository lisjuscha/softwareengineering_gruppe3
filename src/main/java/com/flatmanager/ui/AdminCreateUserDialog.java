package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class AdminCreateUserDialog {

    public static Optional<Boolean> showAndWait(Window owner) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Neuen Benutzer anlegen");
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Benutzername");

        CheckBox adminCheck = new CheckBox("Admin");

        Label passwordLabel = new Label("Passwort:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Passwort");

        Label confirmLabel = new Label("Passwort bestätigen:");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Passwort bestätigen");

        // Standardmäßig Passwortfelder und Labels ausblenden (sichtbar nur bei Admin)
        passwordLabel.setVisible(false);
        passwordLabel.setManaged(false);
        passwordField.setVisible(false);
        passwordField.setManaged(false);

        confirmLabel.setVisible(false);
        confirmLabel.setManaged(false);
        confirmField.setVisible(false);
        confirmField.setManaged(false);

        grid.add(new Label("Benutzername:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(adminCheck, 1, 1);
        grid.add(passwordLabel, 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(confirmLabel, 0, 3);
        grid.add(confirmField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);

        Runnable updateOk = () -> {
            String uname = usernameField.getText() == null ? "" : usernameField.getText().trim();
            boolean admin = adminCheck.isSelected();
            boolean pwOk = true;
            if (admin) {
                String p1 = passwordField.getText() == null ? "" : passwordField.getText();
                String p2 = confirmField.getText() == null ? "" : confirmField.getText();
                pwOk = !p1.isEmpty() && p1.equals(p2);
            }
            okButton.setDisable(uname.isEmpty() || !pwOk);
        };

        // Listener
        usernameField.textProperty().addListener((obs, oldV, newV) -> updateOk.run());
        passwordField.textProperty().addListener((obs, oldV, newV) -> updateOk.run());
        confirmField.textProperty().addListener((obs, oldV, newV) -> updateOk.run());

        // zuverlässiger Listener: selectedProperty statt setOnAction
        adminCheck.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                passwordLabel.setVisible(true);
                passwordLabel.setManaged(true);
                passwordField.setVisible(true);
                passwordField.setManaged(true);

                confirmLabel.setVisible(true);
                confirmLabel.setManaged(true);
                confirmField.setVisible(true);
                confirmField.setManaged(true);

                // Fokus auf Passwortfeld setzen
                Platform.runLater(passwordField::requestFocus);
            } else {
                // Felder ausblenden und Inhalte löschen, damit später keine leeren Passwörter übrig bleiben
                passwordLabel.setVisible(false);
                passwordLabel.setManaged(false);
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                passwordField.clear();

                confirmLabel.setVisible(false);
                confirmLabel.setManaged(false);
                confirmField.setVisible(false);
                confirmField.setManaged(false);
                confirmField.clear();
            }

            // Layout aktualisieren und OK-Button neu bewerten
            grid.requestLayout();
            dialog.getDialogPane().requestLayout();
            updateOk.run();
        });

        // initialer Status
        updateOk.run();
        Platform.runLater(() -> usernameField.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String inputName = usernameField.getText().trim();
                boolean isAdmin = adminCheck.isSelected();

                if (inputName.isEmpty()) {
                    showError("Benutzername darf nicht leer sein.");
                    return false;
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    ensureIsAdminColumn(conn);

                    // Falls Admin: sicherstellen, dass password-Spalte vorhanden ist
                    if (isAdmin) {
                        ensurePasswordColumn(conn);
                    }

                    boolean hasIsAdmin = hasColumn(conn, "is_admin");
                    boolean hasPassword = hasColumn(conn, "password");

                    String userCol = resolveNameColumn(conn);

                    String checkSql = "SELECT 1 FROM users WHERE " + userCol + " = ? COLLATE NOCASE LIMIT 1";
                    try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                        checkPs.setString(1, inputName);
                        try (ResultSet rs = checkPs.executeQuery()) {
                            if (rs.next()) {
                                showError("Benutzername existiert bereits.");
                                return false;
                            }
                        }
                    }

                    // bestimme Passwortwert: Admin -> gehasht, sonst leerer String
                    String passwordToStore = "";
                    if (isAdmin) {
                        String plain = passwordField.getText() == null ? "" : passwordField.getText();
                        if (plain.isEmpty()) {
                            showError("Admin benötigt ein Passwort.");
                            return false;
                        }
                        passwordToStore = hashPassword(plain);
                    }

                    // Baue passenden INSERT je nach vorhandenen Spalten
                    if (hasPassword && hasIsAdmin) {
                        String insert = "INSERT INTO users (" + userCol + ", password, is_admin) VALUES (?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(insert)) {
                            ps.setString(1, inputName);
                            ps.setString(2, passwordToStore);
                            ps.setInt(3, isAdmin ? 1 : 0);
                            ps.executeUpdate();
                        }
                    } else if (hasPassword) {
                        String insert = "INSERT INTO users (" + userCol + ", password) VALUES (?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(insert)) {
                            ps.setString(1, inputName);
                            ps.setString(2, passwordToStore);
                            ps.executeUpdate();
                        }
                    } else if (hasIsAdmin) {
                        String insert = "INSERT INTO users (" + userCol + ", is_admin) VALUES (?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(insert)) {
                            ps.setString(1, inputName);
                            ps.setInt(2, isAdmin ? 1 : 0);
                            ps.executeUpdate();
                        }
                    } else {
                        String insert = "INSERT INTO users (" + userCol + ") VALUES (?)";
                        try (PreparedStatement ps = conn.prepareStatement(insert)) {
                            ps.setString(1, inputName);
                            ps.executeUpdate();
                        }
                    }

                    Alert a = new Alert(Alert.AlertType.INFORMATION);
                    a.setHeaderText(null);
                    a.setContentText("Benutzer erfolgreich angelegt.");
                    a.showAndWait();
                    return true;

                } catch (SQLException ex) {
                    String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                    if (msg.contains("unique") || msg.contains("constraint failed")) {
                        showError("Benutzername existiert bereits (Constraint).");
                    } else if (msg.contains("not null")) {
                        showError("Pflichtfeld fehlt beim Anlegen (NOT NULL Constraint).");
                    } else {
                        showError("Fehler beim Anlegen des Benutzers: " + ex.getMessage());
                    }
                    return false;
                } catch (Exception ex) {
                    showError("Unerwarteter Fehler: " + ex.getMessage());
                    return false;
                }
            }
            return false;
        });

        return dialog.showAndWait();
    }

    private static void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private static boolean hasColumn(Connection conn, String columnName) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        } catch (SQLException e) {
            // ignore
        }
        return false;
    }

    private static String resolveNameColumn(Connection conn) {
        if (hasColumn(conn, "username")) return "username";
        if (hasColumn(conn, "name")) return "name";
        return "username";
    }

    private static void ensureIsAdminColumn(Connection conn) {
        try {
            if (!hasColumn(conn, "is_admin")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE users ADD COLUMN is_admin INTEGER DEFAULT 0");
                }
            }
        } catch (SQLException e) {
            // ignore
        }
    }

    private static void ensurePasswordColumn(Connection conn) {
        try {
            if (!hasColumn(conn, "password")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE users ADD COLUMN password TEXT DEFAULT ''");
                }
            }
        } catch (SQLException e) {
            // ignore
        }
    }

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