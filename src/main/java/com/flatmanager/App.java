package com.flatmanager;

import com.flatmanager.ui.LoginScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Flat Manager");
        // Startfenster etwas größer öffnen
        primaryStage.setWidth(1280);
        primaryStage.setHeight(800);

        // Show login screen
        showLoginScreen();

        primaryStage.show();
    }

    public static void showLoginScreen() {
        LoginScreen loginScreen = new LoginScreen();
        Scene scene = new Scene(loginScreen.getView(), 800, 600);

        // styles.css aus src/main/resources laden
        scene.getStylesheets().add(App.class.getResource("/styles.css").toExternalForm());

        // Apply current theme (light/dark)
        com.flatmanager.ui.ThemeManager.applyToScene(scene);

        primaryStage.setScene(scene);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    @Override
    public void stop() {
        // Falls DatabaseManager entfernt wurde, hier nichts aufrufen
    }

    public static void main(String[] args) {
        launch(args);
    }
}