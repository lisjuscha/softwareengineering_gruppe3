package com.flatmanager.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerAdminTest {

    @BeforeEach
    void before() throws Exception {
        File db = new File("target/db_admin_test.db");
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
    }

    @AfterEach
    void after() {
        DatabaseManager.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/db_admin_test.db")); } catch (Exception ignore) {}
    }

    @Test
    void testCreateHouseholdWithAdminAndListUsers() throws Exception {
        boolean ok = DatabaseManager.createHouseholdWithAdmin("WG","adminUser","secret", List.of(new DatabaseManager.UserData("Alice","alice","pw")));
        assertTrue(ok);

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        // should contain adminUser and alice
        assertTrue(users.stream().anyMatch(u -> "adminUser".equalsIgnoreCase(u.username)));
        assertTrue(users.stream().anyMatch(u -> "alice".equalsIgnoreCase(u.username)));
    }
}

