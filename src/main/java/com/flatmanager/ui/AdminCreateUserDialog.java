package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

public class AdminCreateUserDialog {

    /**
     * Öffnet einen Dialog zum Anlegen eines neuen Benutzers.
     * Rückgabe:
     * - Optional.of(true)  -> Benutzer wurde erfolgreich angelegt
     * - Optional.of(false) -> Dialog abgebrochen oder Fehler (Fehlermeldung wird angezeigt)
     * - Optional.empty()   -> selten, falls showAndWait selbst leer zurückgibt
     */
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

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Passwort");

        CheckBox adminCheck = new CheckBox("Admin");

        grid.add(new Label("Benutzername:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Passwort:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(adminCheck, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);

        // Aktivieren des OK-Buttons nur wenn Felder gefüllt sind
        usernameField.textProperty().addListener((obs, oldV, newV) ->
                okButton.setDisable(newV.trim().isEmpty() || passwordField.getText().trim().isEmpty()));
        passwordField.textProperty().addListener((obs, oldV, newV) ->
                okButton.setDisable(newV.trim().isEmpty() || usernameField.getText().trim().isEmpty()));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String username = usernameField.getText().trim();
                String password = passwordField.getText(); // evtl. später hashen
                boolean isAdmin = adminCheck.isSelected();

                // Versuche, den Benutzer in die DB einzufügen
                String sql = "INSERT INTO users (username, password, is_admin) VALUES (?, ?, ?)";
                try (Connection conn = Database.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {

                    ps.setString(1, username);
                    ps.setString(2, password);
                    ps.setInt(3, isAdmin ? 1 : 0);
                    ps.executeUpdate();
                    return true;
                } catch (SQLException ex) {
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setHeaderText("Fehler beim Anlegen des Benutzers");
                    a.setContentText(ex.getMessage());
                    a.showAndWait();
                    return false;
                } catch (Exception ex) {
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setHeaderText("Unerwarteter Fehler");
                    a.setContentText(ex.getMessage());
                    a.showAndWait();
                    return false;
                }
            }
            return false; // bei CANCEL
        });

        return dialog.showAndWait();
    }
}