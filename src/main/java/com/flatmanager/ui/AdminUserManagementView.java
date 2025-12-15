package com.flatmanager.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class AdminUserManagementView {

    /**
     * Öffnet ein kleines modal Fenster mit den Optionen:
     * - Benutzer anlegen -> öffnet AdminCreateUserDialog
     * - Benutzer löschen -> öffnet AdminDeleteUserDialog (ohne aktuellen Admin in Auswahl)
     * - Abbrechen / schließen -> Optional.empty()
     * <p>
     * Gibt die gewählte Aktion als Optional<String> zurück ("create" / "delete").
     */
    public static Optional<String> showAndWait(Window owner, String currentAdminUsername) {
        AtomicReference<String> result = new AtomicReference<>(null);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Benutzerverwaltung");

        Label label = new Label("Wähle eine Aktion:");
        Button createBtn = new Button("Benutzer anlegen");
        Button deleteBtn = new Button("Benutzer löschen");
        Button cancelBtn = new Button("Abbrechen");

        createBtn.setOnAction(e -> {
            try {
                // Dialog mit *diesem* kleinen Stage als Owner aufrufen
                Optional<Boolean> res = AdminCreateUserDialog.showAndWait(stage);
                if (res.isPresent() && Boolean.TRUE.equals(res.get())) {
                    showInfo("Benutzer wurde angelegt.");
                }
                result.set("create");
            } catch (NoClassDefFoundError ex) {
                showInfo("AdminCreateUserDialog nicht vorhanden.");
            } catch (Exception ex) {
                showInfo("Fehler beim Anlegen: " + ex.getMessage());
            } finally {
                stage.close();
            }
        });

        deleteBtn.setOnAction(e -> {
            try {
                // Dialog mit *diesem* kleinen Stage als Owner aufrufen und aktuellen Adminnamen übergeben
                Optional<Boolean> res = AdminDeleteUserDialog.showAndWait(stage, currentAdminUsername);
                if (res.isPresent() && Boolean.TRUE.equals(res.get())) {
                    showInfo("Benutzer wurde gelöscht.");
                }
                result.set("delete");
            } catch (NoClassDefFoundError ex) {
                showInfo("AdminDeleteUserDialog nicht vorhanden.");
            } catch (Exception ex) {
                showInfo("Fehler beim Löschen: " + ex.getMessage());
            } finally {
                stage.close();
            }
        });

        cancelBtn.setOnAction(e -> {
            result.set(null);
            stage.close();
        });

        HBox actions = new HBox(10, createBtn, deleteBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER);

        VBox root = new VBox(12, label, actions);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.CENTER);

        stage.setScene(new Scene(root));
        stage.showAndWait();

        return Optional.ofNullable(result.get());
    }

    private static void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}