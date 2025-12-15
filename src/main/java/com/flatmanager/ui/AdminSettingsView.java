package com.flatmanager.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

import java.util.Optional;

public class AdminSettingsView {

    public static Node createView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label title = new Label("Admin Einstellungen");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        BorderPane.setAlignment(title, Pos.CENTER_LEFT);
        root.setTop(title);

        VBox content = new VBox(12);
        content.setPadding(new Insets(12));

        Label info = new Label("Hier können Administrationsaufgaben ausgeführt werden.");
        info.setWrapText(true);

        HBox buttons = new HBox(10);
        Button manageUsers = new Button("Benutzer verwalten");
        Button dbExport = new Button("DB exportieren");
        Button appSettings = new Button("Einstellungen");

        manageUsers.setOnAction(e -> {
            Window owner = null;
            if (e.getSource() instanceof javafx.scene.Node) {
                javafx.scene.Node src = (javafx.scene.Node) e.getSource();
                if (src.getScene() != null) owner = src.getScene().getWindow();
            }

            try {
                // showAndWait benötigt jetzt (Window owner, String currentAdminUsername)
                // Falls der aktuelle Admin-Name hier nicht verfügbar ist, null übergeben.
                AdminUserManagementView.showAndWait(owner, null);
            } catch (Exception ex) {
                showInfo("Fehler: " + ex.getMessage());
            }
        });

        dbExport.setOnAction(e -> showInfo("DB-Export noch nicht implementiert."));
        appSettings.setOnAction(e -> showInfo("Einstellungen noch nicht implementiert."));

        buttons.getChildren().addAll(manageUsers, dbExport, appSettings);
        content.getChildren().addAll(info, buttons);
        root.setCenter(content);

        return root;
    }

    private static void showInfo(String msg) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}