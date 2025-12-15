package com.flatmanager.ui;

import com.flatmanager.storage.Database;
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
     * Zeigt ein modal Fenster zum Löschen eines Benutzers.
     * Der Benutzername des aktuellen Admins wird aus der Auswahl ausgeschlossen.
     *
     * Rückgabe:
     * - Optional.of(Boolean.TRUE) wenn der Benutzer bestätigt gelöscht wurde
     * - Optional.empty() bei Abbruch/Schließen
     */
    public static Optional<Boolean> showAndWait(Window owner, String currentAdminUsername) {
        AtomicReference<Boolean> result = new AtomicReference<>(null);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Benutzer löschen");

        Label label = new Label("Benutzer zum Löschen auswählen:");
        List<String> usernames = loadUsernames(owner, currentAdminUsername);
        ComboBox<String> userCombo = new ComboBox<>(FXCollections.observableArrayList(usernames));
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
            confirm.initOwner(stage);
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isPresent() && choice.get() == ButtonType.OK) {
                // Tatsächliche Löschlogik: DELETE FROM users WHERE username = ?
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
                        a.initOwner(stage);
                        a.showAndWait();
                    }
                } catch (SQLException ex) {
                    Alert a = new Alert(AlertType.ERROR);
                    a.setHeaderText(null);
                    a.setContentText("Fehler beim Löschen: " + ex.getMessage());
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

        stage.setScene(new Scene(root));
        stage.showAndWait();

        return Optional.ofNullable(result.get());
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
            if (owner != null) a.initOwner(owner);
            a.showAndWait();
        }
        return list;
    }

    private static void showInfo(String msg, Window owner) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        if (owner != null) a.initOwner(owner);
        a.showAndWait();
    }
}