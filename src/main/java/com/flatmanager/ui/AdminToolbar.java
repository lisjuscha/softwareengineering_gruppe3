package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AdminToolbar {

    /**
     * Liefert ein Node für die Topbar. Nur sichtbar/aktiv für Admins.
     * Der Admin-Button öffnet direkt den AdminCreateUserDialog.
     * Wichtig: Füge das zurückgegebene Node in der Topbar vor dem Logout-Button ein,
     * damit es links neben Logout erscheint.
     */
    public static Node settingsNode(String currentUser) {
        if (currentUser == null) return placeholder();

        boolean admin = isAdmin(currentUser);
        if (!admin) return placeholder();

        Button adminBtn = new Button("Admin");
        adminBtn.setOnAction(e -> {
            try {
                Window owner = adminBtn.getScene() != null ? adminBtn.getScene().getWindow() : null;

                // Direkt den Dialog öffnen (keine neue Stage)
                Optional<Boolean> result = AdminCreateUserDialog.showAndWait(owner);
                if (result.isPresent() && Boolean.TRUE.equals(result.get())) {
                    showInfo("Benutzer wurde angelegt.");
                }
            } catch (NoClassDefFoundError ex) {
                showInfo("AdminCreateUserDialog ist nicht vorhanden.");
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setHeaderText(null);
                a.setContentText("Fehler beim Öffnen des Dialogs: " + ex.getMessage());
                a.showAndWait();
            }
        });

        return adminBtn;
    }

    private static Region placeholder() {
        Region r = new Region();
        r.setMinWidth(0);
        r.setPrefWidth(0);
        r.setMaxWidth(0);
        r.setVisible(false);
        r.setManaged(false);
        return r;
    }

    private static void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    /**
     * Versucht aus der DB zu lesen, ob der Nutzer Admin ist.
     * Fallback: true, wenn username == "admin" (case-insensitive).
     */
    private static boolean isAdmin(String username) {
        if (username == null) return false;
        if ("admin".equalsIgnoreCase(username.trim())) return true;

        String sql = "SELECT is_admin FROM users WHERE username = ? LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int val = rs.getInt("is_admin");
                    return val == 1;
                }
            }
        } catch (SQLException ex) {
            return false;
        }
        return false;
    }
}