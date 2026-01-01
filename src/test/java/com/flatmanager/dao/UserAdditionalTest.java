package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UserAdditionalTest {

    private static final String DB_FILE = "target/user_additional.db";

    @BeforeEach
    void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignore) {}
    }

    @Test
    void testCreateHouseholdWithAdminCreatesAdminAndMembers() throws Exception {
        DatabaseManager.UserData m1 = new DatabaseManager.UserData("Member One", "m1", "pw1");
        DatabaseManager.UserData m2 = new DatabaseManager.UserData("Member Two", "m2", "pw2");
        boolean ok = DatabaseManager.createHouseholdWithAdmin("WG", "adminUser", "adminPass", List.of(m1, m2));
        assertTrue(ok);

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.stream().anyMatch(u -> "adminUser".equalsIgnoreCase(u.username)));
        assertTrue(users.stream().anyMatch(u -> "m1".equalsIgnoreCase(u.username)));
        assertTrue(users.stream().anyMatch(u -> "m2".equalsIgnoreCase(u.username)));

        DatabaseManager.UserInfo admin = users.stream().filter(u -> "adminUser".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(admin);
        assertTrue(admin.isAdmin);
    }

    @Test
    void testCreateOrUpdateUserStoresHashedPassword() throws Exception {
        boolean created = DatabaseManager.createOrUpdateUser("hash_user", "plainpw", "Hash User");
        assertTrue(created);

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        DatabaseManager.UserInfo ui = users.stream().filter(u -> "hash_user".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(ui);
        assertNotNull(ui.passwordHash);
        assertNotEquals("plainpw", ui.passwordHash);
        assertTrue(ui.passwordHash.length() > 0);
    }

    @Test
    void testCreateHouseholdSkipsInvalidMembers() throws Exception {
        DatabaseManager.UserData good = new DatabaseManager.UserData("G", "good", "pw");
        DatabaseManager.UserData bad1 = new DatabaseManager.UserData("Bad", "", "pw");
        DatabaseManager.UserData bad2 = new DatabaseManager.UserData(null, null, "pw");
        boolean ok = DatabaseManager.createHouseholdWithAdmin(null, "admin2", "apw", List.of(good, bad1, bad2));
        // createHouseholdWithAdmin accepts null wgName but requires adminUsername/password; should create admin and only good member
        assertTrue(ok);
        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.stream().anyMatch(u -> "admin2".equalsIgnoreCase(u.username)));
        assertTrue(users.stream().anyMatch(u -> "good".equalsIgnoreCase(u.username)));
        assertFalse(users.stream().anyMatch(u -> "".equals(u.username)));
    }
}

