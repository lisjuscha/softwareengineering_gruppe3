// java
package com.flatmanager;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.ui.LoginScreen;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("WG-App");
        primaryStage.setWidth(1300);
        primaryStage.setHeight(800);

        // Initialize database
        try {
            DatabaseManager.getConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Show login screen
        showLoginScreen();

        primaryStage.show();
    }

    public static void showLoginScreen() {
        LoginScreen loginScreen = new LoginScreen();
        Scene scene = new Scene(loginScreen.getView(), 800, 600);
        scene.getStylesheets().add(App.class.getResource("/styles.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    @Override
    public void stop() {
        DatabaseManager.closeConnection();
    }

    public static void main(String[] args) {
        launch(args);
    }
}