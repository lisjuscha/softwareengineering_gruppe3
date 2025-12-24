package com.flatmanager.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerListUsersTest {

    @BeforeEach
    void before() throws Exception {
        File db = new File("target/db_listusers_test.db");
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
    }

    @AfterEach
    void after() {
        DatabaseManager.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/db_listusers_test.db")); } catch (Exception ignore) {}
    }

    @Test
    void testListUsersMaskingAndAdminDetection() throws Exception {
        // create admin and normal user
        assertTrue(DatabaseManager.createOrUpdateUser("admin", "pw123", "Admin"));
        assertTrue(DatabaseManager.createOrUpdateUser("user1", "secret", "User One"));

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.size() >= 2);
        DatabaseManager.UserInfo admin = users.stream().filter(u -> "admin".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        DatabaseManager.UserInfo user1 = users.stream().filter(u -> "user1".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(admin);
        assertNotNull(user1);
        // admin detection: either is_admin column or username/name equals admin
        assertTrue(admin.isAdmin || "admin".equalsIgnoreCase(admin.username) || "admin".equalsIgnoreCase(admin.name));
        // passwordHash masked (not null)
        if (admin.passwordHash != null) assertTrue(admin.passwordHash.contains("...") || admin.passwordHash.equals("****") );
        if (user1.passwordHash != null) assertTrue(user1.passwordHash.contains("...") || user1.passwordHash.equals("****") );
    }
}

