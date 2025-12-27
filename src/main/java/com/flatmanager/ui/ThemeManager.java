package com.flatmanager.ui;

import com.flatmanager.App;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;

public final class ThemeManager {
    private static boolean dark = false;

    private ThemeManager() {}

    public static boolean isDark() {
        return dark;
    }

    public static void applyToScene(Scene scene) {
        if (scene == null) return;
        Node root = scene.getRoot();
        if (root == null) return;
        if (dark) {
            if (!root.getStyleClass().contains("dark-mode")) root.getStyleClass().add("dark-mode");
            // also add dark-mode to known view containers to increase selector specificity
            try {
                for (Node n : root.lookupAll(".login-container")) n.getStyleClass().add("dark-mode");
                for (Node n : root.lookupAll(".dashboard")) n.getStyleClass().add("dark-mode");
                for (Node n : root.lookupAll(".budget-view")) n.getStyleClass().add("dark-mode");
                for (Node n : root.lookupAll(".shopping-view")) n.getStyleClass().add("dark-mode");
                for (Node n : root.lookupAll(".cleaning-view")) n.getStyleClass().add("dark-mode");
            } catch (Exception ignored) {}
        } else {
            root.getStyleClass().remove("dark-mode");
            try {
                for (Node n : root.lookupAll(".login-container")) n.getStyleClass().remove("dark-mode");
                for (Node n : root.lookupAll(".dashboard")) n.getStyleClass().remove("dark-mode");
                for (Node n : root.lookupAll(".budget-view")) n.getStyleClass().remove("dark-mode");
                for (Node n : root.lookupAll(".shopping-view")) n.getStyleClass().remove("dark-mode");
                for (Node n : root.lookupAll(".cleaning-view")) n.getStyleClass().remove("dark-mode");
            } catch (Exception ignored) {}
        }
    }

    // Apply stylesheets and dark-mode class to a DialogPane (used by Alerts/Dialogs)
    public static void styleDialogPane(javafx.scene.control.DialogPane pane) {
        if (pane == null) return;
        try {
            String css = com.flatmanager.App.class.getResource("/styles.css").toExternalForm();
            if (!pane.getStylesheets().contains(css)) pane.getStylesheets().add(css);
        } catch (Exception ignored) {}
        if (dark) {
            if (!pane.getStyleClass().contains("dark-mode")) pane.getStyleClass().add("dark-mode");
        } else {
            pane.getStyleClass().remove("dark-mode");
        }
    }

    public static void toggle() {
        dark = !dark;
        // apply to current scene on FX thread
        Platform.runLater(() -> {
            if (App.getPrimaryStage() != null && App.getPrimaryStage().getScene() != null) {
                applyToScene(App.getPrimaryStage().getScene());
            }
        });
    }

    public static void ensureCurrentScene() {
        if (App.getPrimaryStage() != null && App.getPrimaryStage().getScene() != null) {
            applyToScene(App.getPrimaryStage().getScene());
        }
    }
}
