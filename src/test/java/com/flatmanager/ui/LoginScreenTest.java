package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class LoginScreenTest {
    private static final String DB_FILE = "target/login_test.db";

    @BeforeAll
    public static void initToolkit() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignore) {}
    }

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) Files.delete(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS users (username TEXT UNIQUE, password TEXT, name TEXT, is_admin INTEGER DEFAULT 0)");
            }
        }
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    @Test
    public void testLoadUsersWhenNoneShowsRegisterPrompt() throws Exception {
        // no users in DB
        LoginScreen ls = new LoginScreen();
        // access private usersPane via reflection
        Field usersPaneField = LoginScreen.class.getDeclaredField("usersPane");
        usersPaneField.setAccessible(true);
        Object pane = usersPaneField.get(ls);
        assertNotNull(pane);
        // pane should be a javafx Pane with children showing 'Keine Benutzer gefunden.' wrapper
        // ensure there is at least one child (the wrapper)
        javafx.scene.layout.Pane usersPane = (javafx.scene.layout.Pane) pane;
        assertTrue(usersPane.getChildren().size() > 0);
        // first child contains a Label and a Button (wrapper VBox) -> check types
        Node first = usersPane.getChildren().get(0);
        assertTrue(first instanceof VBox);
    }

    @Test
    public void testLoadUsersAddsTilesAndAuthenticateAdmin() throws Exception {
        // insert admin user with hashed password
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, password, is_admin) VALUES (?, ?, ?)")) {
                ps.setString(1, "adminUser");
                // compute hash same as LoginScreen.hashPassword
                Method hash = LoginScreen.class.getDeclaredMethod("hashPassword", String.class);
                hash.setAccessible(true);
                String hashed = (String) hash.invoke(null, "s3cret");
                ps.setString(2, hashed);
                ps.setInt(3, 1);
                ps.executeUpdate();
            }
        }

        LoginScreen ls = new LoginScreen();
        Field usersPaneField = LoginScreen.class.getDeclaredField("usersPane");
        usersPaneField.setAccessible(true);
        javafx.scene.layout.Pane usersPane = (javafx.scene.layout.Pane) usersPaneField.get(ls);
        // should have at least one user tile
        assertTrue(usersPane.getChildren().size() >= 1);

        // test authenticateAdmin via reflection
        Method auth = LoginScreen.class.getDeclaredMethod("authenticateAdmin", String.class, String.class);
        auth.setAccessible(true);
        boolean ok = (Boolean) auth.invoke(ls, "adminUser", "s3cret");
        assertTrue(ok);
        boolean fail = (Boolean) auth.invoke(ls, "adminUser", "wrong");
        assertFalse(fail);
    }
}
