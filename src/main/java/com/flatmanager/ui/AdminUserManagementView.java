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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class AdminUserManagementView {

    public static Optional<String> showAndWait(Window owner, String currentAdminUsername) {
        AtomicReference<String> result = new AtomicReference<>(null);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Benutzerverwaltung");

        Label label = new Label("Wähle eine Aktion:");
        Button createBtn = new Button("Benutzer anlegen");
        Button deleteBtn = new Button("Benutzer löschen");
        Button deleteWgBtn = new Button("WG löschen");
        Button cancelBtn = new Button("Abbrechen");

        createBtn.setOnAction(e -> {
            try {
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
                // Wichtig: übergebe das tatsächliche owner-Fenster weiter, nicht null
                Optional<Boolean> res = AdminDeleteUserDialog.showAndWait(stage, currentAdminUsername, () -> {
                    openLoginWithReflection(owner);
                });
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

        deleteWgBtn.setOnAction(e -> {
            try {
                Optional<Boolean> res = AdminDeleteUserDialog.deleteEntireWg(stage, () -> openLoginWithReflection(owner));
                if (res.isPresent() && Boolean.TRUE.equals(res.get())) {
                    showInfo("WG wurde gelöscht.");
                }
                result.set("delete-wg");
            } catch (NoClassDefFoundError ex) {
                showInfo("AdminDeleteUserDialog nicht vorhanden.");
            } catch (Exception ex) {
                showInfo("Fehler beim Löschen der WG: " + ex.getMessage());
            } finally {
                stage.close();
            }
        });

        cancelBtn.setOnAction(e -> {
            result.set(null);
            stage.close();
        });

        HBox actions = new HBox(10, createBtn, deleteBtn, deleteWgBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER);

        VBox root = new VBox(12, label, actions);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.CENTER);

        stage.setScene(new Scene(root));
        stage.showAndWait();

        return Optional.ofNullable(result.get());
    }

    /**
     * Verbesserte Reflection: probiert verschiedene Signaturen, ContextClassLoader und
     * sowohl statische als auch Instanzmethoden. Loggt Fehler auf stderr für Debugging.
     */
    private static void openLoginWithReflection(Window owner) {
        String[] candidates = {
                "com.flatmanager.ui.LoginView",
                "com.flatmanager.ui.LoginScreen",
                "LoginView",
                "LoginScreen"
        };

        for (String clsName : candidates) {
            try {
                ClassLoader[] loaders = {
                        Thread.currentThread().getContextClassLoader(),
                        AdminUserManagementView.class.getClassLoader(),
                        ClassLoader.getSystemClassLoader()
                };
                Class<?> cls = null;
                for (ClassLoader loader : loaders) {
                    if (loader == null) continue;
                    try {
                        cls = Class.forName(clsName, false, loader);
                        break;
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                if (cls == null) continue;

                // mögliche Methodensignaturen in Reihenfolge der Wahrscheinlichkeit
                String[][] sigs = {
                        {"showLogin", "javafx.stage.Window"},
                        {"showLogin", "javafx.stage.Stage"},
                        {"showLogin", ""},
                        {"show", "javafx.stage.Window"},
                        {"show", "javafx.stage.Stage"},
                        {"show", ""}
                };

                for (String[] sig : sigs) {
                    try {
                        Method m;
                        if (!sig[1].isEmpty()) {
                            Class<?> param = Class.forName(sig[1]);
                            m = cls.getMethod(sig[0], param);
                        } else {
                            m = cls.getMethod(sig[0]);
                        }
                        m.setAccessible(true);
                        if (m.getParameterCount() == 1) {
                            m.invoke(null, owner);
                        } else {
                            m.invoke(null);
                        }
                        return; // erfolgreich
                    } catch (NoSuchMethodException nsme) {
                        // versuche Instanzmethode als Fallback
                        try {
                            Method mInst;
                            if (!sig[1].isEmpty()) {
                                Class<?> param = Class.forName(sig[1]);
                                mInst = cls.getMethod(sig[0], param);
                            } else {
                                mInst = cls.getMethod(sig[0]);
                            }
                            Object inst = cls.getDeclaredConstructor().newInstance();
                            mInst.setAccessible(true);
                            if (mInst.getParameterCount() == 1) {
                                mInst.invoke(inst, owner);
                            } else {
                                mInst.invoke(inst);
                            }
                            return;
                        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                                 InvocationTargetException | ClassNotFoundException instEx) {
                            // nicht gefunden/erzeugbar — weiter probieren
                        }
                    }
                }
            } catch (Throwable t) {
                // Logge Fehler für späteres Debugging, aber probiere nächsten Kandidaten
                System.err.println("openLoginWithReflection: Fehler bei Kandidat " + clsName + " - " + t);
                t.printStackTrace(System.err);
            }
        }

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText("LoginView/Screen konnte nicht geöffnet werden. Bitte Anwendung neu starten.");
        a.showAndWait();
    }

    private static void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}