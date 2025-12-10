package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class AdminCreateUserDialog {

    private AdminCreateUserDialog() {
    }

    public static void show(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Admin: Neuen Benutzer anlegen");

        GridPane gp = new GridPane();
        gp.setPadding(new Insets(10));
        gp.setHgap(10);
        gp.setVgap(10);

        TextField username = new TextField();
        PasswordField password = new PasswordField();
        TextField name = new TextField();

        gp.add(new Label("Benutzername:"), 0, 0);
        gp.add(username, 1, 0);
        gp.add(new Label("Passwort:"), 0, 1);
        gp.add(password, 1, 1);
        gp.add(new Label("Anzeigename:"), 0, 2);
        gp.add(name, 1, 2);

        Button createBtn = new Button("Anlegen / Aktualisieren");
        createBtn.setDefaultButton(true);
        createBtn.setOnAction(evt -> {
            String u = username.getText();
            String p = password.getText();
            String n = name.getText();
            boolean ok = DatabaseManager.createOrUpdateUser(u, p, n);
            Alert a = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            a.initOwner(dialog);
            a.setHeaderText(null);
            a.setContentText(ok ? "Benutzer erfolgreich angelegt/aktualisiert." : "Fehler beim Anlegen/Aktualisieren.");
            a.showAndWait();
            if (ok) dialog.close();
        });

        gp.add(createBtn, 0, 3, 2, 1);

        dialog.setScene(new Scene(gp));
        dialog.showAndWait();
    }
}