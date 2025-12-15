package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.database.DatabaseManager.UserData;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RegistrationView {

    /**
     * Öffnet ein modales Fenster zur Anlage einer WG mit Admin und optionalen Mitgliedern.
     * onResult.accept(true) wird bei Erfolg aufgerufen, sonst accept(false).
     */
    public static void showRegistration(Stage owner, Consumer<Boolean> onResult) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("WG Registrierung");

        BorderPane root = new BorderPane();
        root.setPrefWidth(600);
        root.setPrefHeight(420);

        VBox form = new VBox(10);
        form.setPadding(new Insets(16));
        form.setAlignment(Pos.TOP_LEFT);

        TextField wgField = new TextField();
        wgField.setPromptText("WG Name");

        TextField adminUser = new TextField();
        adminUser.setPromptText("Admin Benutzername");

        PasswordField adminPass = new PasswordField();
        adminPass.setPromptText("Admin Passwort");

        TextArea membersArea = new TextArea();
        membersArea.setPromptText("Zusätzliche Mitglieder (eine pro Zeile: displayName,username,password)");

        form.getChildren().addAll(new Label("WG Name:"), wgField,
                new Label("Admin Benutzername:"), adminUser,
                new Label("Admin Passwort:"), adminPass,
                new Label("Zusätzliche Mitglieder (optional):"), membersArea);

        root.setCenter(form);

        Button createBtn = new Button("Erstellen");
        Button cancelBtn = new Button("Abbrechen");
        createBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);

        createBtn.setOnAction(ev -> {
            String wg = wgField.getText().trim();
            String admin = adminUser.getText().trim();
            String pass = adminPass.getText();

            if (admin.isEmpty() || pass == null || pass.isEmpty()) {
                showAlert(stage, "Admin Benutzername und Passwort sind erforderlich.");
                return;
            }

            List<UserData> members = new ArrayList<>();
            String[] lines = membersArea.getText().split("\\R");
            for (String l : lines) {
                if (l == null) continue;
                String s = l.trim();
                if (s.isEmpty()) continue;
                String[] parts = s.split(",", 3);
                String display = parts.length > 0 ? parts[0].trim() : "";
                String user = parts.length > 1 ? parts[1].trim() : "";
                String p = parts.length > 2 ? parts[2].trim() : "";
                if (user.isEmpty()) continue;
                members.add(new UserData(display.isEmpty() ? user : display, user, p));
            }

            boolean ok = DatabaseManager.createHouseholdWithAdmin(wg, admin, pass, members);
            if (ok) {
                showAlert(stage, "WG und Benutzer erfolgreich angelegt.");
                stage.close();
                if (onResult != null) onResult.accept(true);
            } else {
                showAlert(stage, "Anlegen fehlgeschlagen. Bitte Schema prüfen und Logs lesen.");
                if (onResult != null) onResult.accept(false);
            }
        });

        cancelBtn.setOnAction(ev -> {
            stage.close();
            if (onResult != null) onResult.accept(false);
        });

        VBox bottom = new VBox(8, createBtn, cancelBtn);
        bottom.setPadding(new Insets(12));
        bottom.setAlignment(Pos.CENTER_RIGHT);

        root.setBottom(bottom);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static void showAlert(Stage owner, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(owner);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}