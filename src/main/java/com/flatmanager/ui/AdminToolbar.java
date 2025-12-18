package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminToolbar {

    public static Node settingsNode(String currentUser) {
        if (currentUser == null) return placeholder();

        boolean admin = isAdmin(currentUser);
        if (!admin) return placeholder();

        Button adminBtn = new Button();
        // Icon aus den Projekt-Ressourcen laden (/icons/Einstellungen.png), bei Fehler Fallback-Text verwenden
        final String resourcePath = "/icons/Einstellungen.png";
        try (InputStream is = AdminToolbar.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Image img = new Image(is, 20, 20, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(20);
                iv.setFitHeight(20);
                adminBtn.setGraphic(iv);
                adminBtn.setText(null);
                adminBtn.setPrefSize(30, 30);
            } else {
                adminBtn.setText("Admin");
            }
        } catch (Exception ex) {
            adminBtn.setText("Admin");
        }

        adminBtn.setOnAction(e -> {
            try {
                Window owner = adminBtn.getScene() != null ? adminBtn.getScene().getWindow() : null;

                // aktuellen Admin-Benutzernamen weitergeben
                AdminUserManagementView.showAndWait(owner, currentUser);

            } catch (NoClassDefFoundError ex) {
                showInfo("AdminUserManagementView ist nicht vorhanden.");
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