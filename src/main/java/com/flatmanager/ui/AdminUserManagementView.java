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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Admin-Oberfläche für Verwaltungsaktionen: Benutzer anlegen, löschen oder die gesamte WG löschen.
 * Öffnet modale Dialoge und führt die gewünschten Aktionen aus.
 */
public class AdminUserManagementView {

    /**
     * Öffnet ein modales Verwaltungsfenster für Admin-Aktionen und gibt die gewählte Aktion als Optional zurück
     * ("create", "delete", "delete-wg" oder leer bei Abbruch).
     * @param owner optionales Besitzer-Fenster
     * @param currentAdminUsername aktuell angemeldeter Admin-Username (wird an Delete-Dialog übergeben)
     * @return Optional mit Aktions-Schlüssel oder leer bei Abbruch
     */
    public static Optional<String> showAndWait(Window owner, String currentAdminUsername) {
        AtomicReference<String> result = new AtomicReference<>(null);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Benutzerverwaltung");

        Label header = new Label("Benutzerverwaltung");
        header.getStyleClass().add("title");
        header.setWrapText(true);

        Label label = new Label("Wähle eine Aktion:");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        Button createBtn = new Button("Benutzer anlegen"); createBtn.setWrapText(true); createBtn.setMaxWidth(Double.MAX_VALUE);
        Button deleteBtn = new Button("Benutzer löschen"); deleteBtn.setWrapText(true); deleteBtn.setMaxWidth(Double.MAX_VALUE);
        Button deleteWgBtn = new Button("WG löschen"); deleteWgBtn.setWrapText(true); deleteWgBtn.setMaxWidth(Double.MAX_VALUE);
        Button cancelBtn = new Button("Abbrechen"); cancelBtn.setWrapText(true); cancelBtn.setMaxWidth(Double.MAX_VALUE);

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
                // Meldung "WG wurde gelöscht." entfernt, da bereits nach dem Loginscreen-Aufruf angezeigt wird
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

        VBox root = new VBox(12, header, label, actions);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.CENTER);

        // Mark this root as a dialog pane so dialog CSS rules apply, and add dark-mode class when active
        root.getStyleClass().add("dialog-pane");
        if (com.flatmanager.ui.ThemeManager.isDark()) {
            if (!root.getStyleClass().contains("dark-mode")) root.getStyleClass().add("dark-mode");
            // Inline fallback style to guarantee dark background for modal content (helps on macOS where some popups can ignore stylesheet selectors)
            root.setStyle("-fx-background-color: linear-gradient(#071826, #041223); -fx-text-fill: #e6eef8;");
        } else {
            root.getStyleClass().remove("dark-mode");
            root.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #111827;");
        }
        // Create scene, attach main stylesheet and apply theme so the modal respects dark-mode
        Scene scene = new Scene(root);
        // Ensure the Scene fill matches the theme (helps on macOS where window chrome/scene background may be white)
        try {
            if (com.flatmanager.ui.ThemeManager.isDark()) {
                scene.setFill(javafx.scene.paint.Color.web("#071826"));
            } else {
                scene.setFill(javafx.scene.paint.Color.web("#ffffff"));
            }
        } catch (Exception ignored) {}
        try {
            String css = com.flatmanager.App.class.getResource("/styles.css").toExternalForm();
            if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
        } catch (Exception ignored) {}
        com.flatmanager.ui.ThemeManager.applyToScene(scene);
        stage.setScene(scene);
        stage.showAndWait();

        return Optional.ofNullable(result.get());
    }

    // ersetzt die bestehende openLoginWithReflection-Methode in `AdminUserManagementView.java`
    private static void openLoginWithReflection(Window owner) {
        String[] candidates = {
                "com.flatmanager.ui.LoginView",
                "com.flatmanager.ui.LoginScreen",
                "LoginView",
                "LoginScreen"
        };

        final AtomicReference<Throwable> lastThrowableRef = new AtomicReference<>(null);

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
                        final Method m;
                        if (!sig[1].isEmpty()) {
                            Class<?> param = Class.forName(sig[1]);
                            m = cls.getMethod(sig[0], param);
                        } else {
                            m = cls.getMethod(sig[0]);
                        }
                        m.setAccessible(true);

                        // Führt den UI-Aufruf sicher auf dem JavaFX-Application-Thread aus.
                        final boolean takesParam = m.getParameterCount() == 1;
                        javafx.application.Platform.runLater(() -> {
                            try {
                                if (takesParam) {
                                    m.invoke(null, owner);
                                } else {
                                    m.invoke(null);
                                }
                            } catch (InvocationTargetException ite) {
                                Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                                lastThrowableRef.set(cause);
                                System.err.println("openLoginWithReflection: InvocationTargetException beim Aufruf von " + clsName + "." + sig[0]);
                                cause.printStackTrace(System.err);
                                // Zeige sofort einen Alert mit der Ursache
                                javafx.application.Platform.runLater(() -> {
                                    Alert a = new Alert(Alert.AlertType.ERROR);
                                    a.setHeaderText("Fehler beim Öffnen des Logins");
                                    a.setContentText(cause.toString());
                                    com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                                    if (owner != null) a.initOwner(owner);
                                    a.showAndWait();
                                });
                            } catch (Throwable t) {
                                lastThrowableRef.set(t);
                                System.err.println("openLoginWithReflection: Fehler beim Aufruf von " + clsName + "." + sig[0] + " - " + t);
                                t.printStackTrace(System.err);
                                javafx.application.Platform.runLater(() -> {
                                    Alert a = new Alert(Alert.AlertType.ERROR);
                                    a.setHeaderText("Fehler beim Öffnen des Logins");
                                    a.setContentText(t.toString());
                                    com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                                    if (owner != null) a.initOwner(owner);
                                    a.showAndWait();
                                });
                            }
                        });

                        return;
                    } catch (NoSuchMethodException nsme) {
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

                            final boolean takesParamInst = mInst.getParameterCount() == 1;
                            final Method toInvoke = mInst;
                            final Object instance = inst;

                            javafx.application.Platform.runLater(() -> {
                                try {
                                    if (takesParamInst) {
                                        toInvoke.invoke(instance, owner);
                                    } else {
                                        toInvoke.invoke(instance);
                                    }
                                } catch (InvocationTargetException ite) {
                                    Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                                    lastThrowableRef.set(cause);
                                    System.err.println("openLoginWithReflection: InvocationTargetException beim Instanz-Aufruf von " + clsName + "." + sig[0]);
                                    cause.printStackTrace(System.err);
                                    javafx.application.Platform.runLater(() -> {
                                        Alert a = new Alert(Alert.AlertType.ERROR);
                                        a.setHeaderText("Fehler beim Öffnen des Logins");
                                        a.setContentText(cause.toString());
                                        com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                                        if (owner != null) a.initOwner(owner);
                                        a.showAndWait();
                                    });
                                } catch (Throwable t) {
                                    lastThrowableRef.set(t);
                                    System.err.println("openLoginWithReflection: Fehler beim Instanz-Aufruf von " + clsName + "." + sig[0] + " - " + t);
                                    t.printStackTrace(System.err);
                                    javafx.application.Platform.runLater(() -> {
                                        Alert a = new Alert(Alert.AlertType.ERROR);
                                        a.setHeaderText("Fehler beim Öffnen des Logins");
                                        a.setContentText(t.toString());
                                        com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                                        if (owner != null) a.initOwner(owner);
                                        a.showAndWait();
                                    });
                                }
                            });

                            return;
                        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                                 InvocationTargetException | ClassNotFoundException instEx) {
                            lastThrowableRef.set(instEx);
                            // weiter probieren
                        }
                    }
                }
            } catch (Throwable t) {
                lastThrowableRef.set(t);
                System.err.println("openLoginWithReflection: Fehler bei Kandidat " + clsName + " - " + t);
                t.printStackTrace(System.err);
            }
        }

        final String content = "loginscreen/view konnte nicht geöffnet werden, starten sie die anwendung erneut";
        Throwable last = lastThrowableRef.get();
        if (last != null && last.getMessage() != null) {
            final String extended = content + "\n\nFehler: " + last.toString();
            javafx.application.Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setHeaderText(null);
                a.setContentText(extended);
                com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
                if (owner != null) a.initOwner(owner);
                a.showAndWait();
            });
            return;
        }

        javafx.application.Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText(null);
            a.setContentText(content);
            com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
            if (owner != null) a.initOwner(owner);
            a.showAndWait();
        });
    }

    private static void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        com.flatmanager.ui.ThemeManager.styleDialogPane(a.getDialogPane());
        Stage primary = com.flatmanager.App.getPrimaryStage();
        if (primary != null) a.initOwner(primary);
        a.showAndWait();
    }
}
