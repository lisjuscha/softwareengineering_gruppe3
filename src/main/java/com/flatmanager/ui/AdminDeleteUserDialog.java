package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class AdminDeleteUserDialog {

    /**
     * showAndWait jetzt mit einer Callback, die den Login-Screen öffnet.
     * openLoginAction darf null sein; dann wird eine Info angezeigt.
     */
    public static Optional<Boolean> showAndWait(Window owner, String currentAdminUsername, Runnable openLoginAction) {
        AtomicReference<Boolean> result = new AtomicReference<>(null);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Benutzer / WG löschen");

        Label label = new Label("Benutzer zum Löschen auswählen:");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        List<String> usernames = loadUsernames(owner, currentAdminUsername);
        ComboBox<String> userCombo = new ComboBox<>(FXCollections.observableArrayList(usernames));
        userCombo.setMaxWidth(Double.MAX_VALUE);
        userCombo.setPromptText("Benutzer auswählen");

        Button deleteBtn = new Button("Löschen");
        Button cancelBtn = new Button("Abbrechen");
        deleteBtn.setDisable(true);

        userCombo.valueProperty().addListener((obs, oldV, newV) ->
                deleteBtn.setDisable(newV == null || newV.trim().isEmpty())
        );

        deleteBtn.setOnAction(e -> {
            String user = userCombo.getValue();
            if (user == null || user.trim().isEmpty()) return;

            Alert confirm = new Alert(AlertType.CONFIRMATION);
            confirm.setHeaderText(null);
            confirm.setContentText("Benutzer '" + user + "' wirklich löschen?");
            // style and owner
            com.flatmanager.ui.ThemeManager.styleDialogPane(confirm.getDialogPane());
            confirm.initOwner(stage);
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isPresent() && choice.get() == ButtonType.OK) {
                String sqlDelete = "DELETE FROM users WHERE username = ?";
                try (Connection conn = Database.getConnection();
                     PreparedStatement psDel = conn.prepareStatement(sqlDelete)) {

                    psDel.setString(1, user);
                    int affected = psDel.executeUpdate();
                    if (affected > 0) {
                        showInfo("Benutzer '" + user + "' wurde gelöscht.", stage);
                        result.set(Boolean.TRUE);
                        stage.close();
                    } else {
                        Alert a = new Alert(AlertType.ERROR);
                        a.setHeaderText(null);
                        a.setContentText("Benutzer konnte nicht gelöscht werden (nicht gefunden).");
                        com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                        a.initOwner(stage);
                        a.showAndWait();
                    }
                } catch (SQLException ex) {
                    Alert a = new Alert(AlertType.ERROR);
                    a.setHeaderText(null);
                    a.setContentText("Fehler beim Löschen: " + ex.getMessage());
                    com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                    a.initOwner(stage);
                    a.showAndWait();
                }
            }
        });

        cancelBtn.setOnAction(e -> {
            result.set(null);
            stage.close();
        });

        HBox actions = new HBox(10, deleteBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER);

        VBox root = new VBox(12, label, userCombo, actions);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.CENTER_LEFT);

        // create scene and ensure stylesheet + theme are applied so the dialog respects dark mode
        Scene scene = new Scene(root);
        try {
            String css = com.flatmanager.App.class.getResource("/styles.css").toExternalForm();
            if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
        } catch (Exception ignored) {}
        com.flatmanager.ui.ThemeManager.applyToScene(scene);
        stage.setScene(scene);
        stage.showAndWait();

        return Optional.ofNullable(result.get());
    }

    /**
     * Extrahierte Methode: löscht alle Tabellen (WG & Einträge) nach Bestätigung.
     * Schließt optionales Owner-Stage und führt openLoginAction aus.
     */
    public static Optional<Boolean> deleteEntireWg(Window owner, Runnable openLoginAction) {
        AtomicReference<Boolean> result = new AtomicReference<>(null);

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setHeaderText(null);
        confirm.setContentText("ALLE Benutzer (inkl. Admin) und alle Einträge wirklich löschen?\nDieser Vorgang ist unwiederbringlich und startet die Registrierung neu.");
        com.flatmanager.ui.ThemeManager.styleDialogPane(confirm.getDialogPane());
        if (owner != null) confirm.initOwner(owner);
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isPresent() && choice.get() == ButtonType.OK) {
            String[] tables = {"cleaning_tasks", "budget_transactions", "shopping_items", "users"};
            List<String> missing = new ArrayList<>();
            try (Connection conn = Database.getConnection()) {
                boolean previousAuto = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    for (String tbl : tables) {
                        if (!tableExists(conn, tbl)) {
                            missing.add(tbl);
                            continue;
                        }
                        String sql = "DELETE FROM " + tbl;
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.executeUpdate();
                        }
                    }
                    conn.commit();

                    String msg = "WG und alle Einträge wurden gelöscht.";
                    if (!missing.isEmpty()) {
                        msg += " Einige Tabellen fehlten und wurden übersprungen: " + String.join(", ", missing) + ".";
                    }
                    showInfo(msg, owner);
                    result.set(Boolean.TRUE);

                    // Owner-Fenster schließen und Login öffnen auf dem JavaFX-Thread
                    Platform.runLater(() -> {
                        if (owner instanceof Stage) {
                            try {
                                ((Stage) owner).close();
                            } catch (Exception ignored) {
                            }
                        }
                        if (openLoginAction != null) {
                            try {
                                openLoginAction.run();
                            } catch (Throwable t) {
                                showInfo("Fehler beim Öffnen des Login-Screens: " + t.getMessage(), null);
                            }
                        } else {
                            showInfo("LoginView/Screen konnte nicht geöffnet werden. Bitte Anwendung neu starten.", null);
                        }
                    });

                } catch (SQLException ex) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        // Ignorieren
                    }
                    Alert a = new Alert(AlertType.ERROR);
                    a.setHeaderText(null);
                    a.setContentText("Fehler beim Löschen der WG-Daten: " + ex.getMessage());
                    com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                    if (owner != null) a.initOwner(owner);
                    a.showAndWait();
                } finally {
                    try {
                        conn.setAutoCommit(previousAuto);
                    } catch (SQLException ignored) {
                    }
                }
            } catch (SQLException ex) {
                Alert a = new Alert(AlertType.ERROR);
                a.setHeaderText(null);
                a.setContentText("Datenbankfehler: " + ex.getMessage());
                com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                if (owner != null) a.initOwner(owner);
                a.showAndWait();
            }
        }

        return Optional.ofNullable(result.get());
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<String> loadUsernames(Window owner, String currentAdminUsername) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String u = rs.getString("username");
                if (u == null) continue;
                String trimmed = u.trim();
                if (currentAdminUsername != null && trimmed.equalsIgnoreCase(currentAdminUsername.trim())) {
                    // aktuellen Admin ausschließen
                    continue;
                }
                list.add(trimmed);
            }
        } catch (SQLException ex) {
            Alert a = new Alert(AlertType.ERROR);
            a.setHeaderText(null);
            a.setContentText("Fehler beim Laden der Benutzer: " + ex.getMessage());
            com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
            if (owner != null) a.initOwner(owner);
            a.showAndWait();
        }
        return list;
    }

    private static void showInfo(String msg, Window owner) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
        if (owner != null) a.initOwner(owner);
        a.showAndWait();
    }
}