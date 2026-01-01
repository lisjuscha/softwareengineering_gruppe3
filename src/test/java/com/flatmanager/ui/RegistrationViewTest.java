package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.storage.Database;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RegistrationViewTest {
    private static final String DB_FILE = "target/registration_test.db";

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
        // DatabaseManager.ensureSchema will create required tables on first connection
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            // no-op: first connection triggers schema creation
        }
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    @Test
    public void testCreateHouseholdWithAdminCreatesAdminAndMembers() throws Exception {
        boolean ok = DatabaseManager.createHouseholdWithAdmin("MyWG", "admin1", "pw123", List.of(
                new DatabaseManager.UserData("Alice","alice","a1"),
                new DatabaseManager.UserData("Bob","bob","b1")
        ));
        assertTrue(ok);

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.stream().anyMatch(u -> "admin1".equalsIgnoreCase(u.username) && u.isAdmin));
        assertTrue(users.stream().anyMatch(u -> "alice".equalsIgnoreCase(u.username) && !u.isAdmin));
        assertTrue(users.stream().anyMatch(u -> "bob".equalsIgnoreCase(u.username) && !u.isAdmin));
    }

    @Test
    public void testCreateHouseholdValidationFailsForMissingAdmin() throws Exception {
        boolean ok1 = DatabaseManager.createHouseholdWithAdmin("WG", null, "pw", null);
        assertFalse(ok1);
        boolean ok2 = DatabaseManager.createHouseholdWithAdmin("WG", " ", "pw", null);
        assertFalse(ok2);
        boolean ok3 = DatabaseManager.createHouseholdWithAdmin("WG", "admin2", "", null);
        assertFalse(ok3);
    }
}
