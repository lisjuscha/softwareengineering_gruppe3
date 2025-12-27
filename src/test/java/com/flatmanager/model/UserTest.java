package com.flatmanager.model;

import com.flatmanager.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    private static final String DB_PATH = "target/user_integ.db";

    @BeforeEach
    void setupDb() throws Exception {
        File db = new File(DB_PATH);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
    }

    @AfterEach
    void cleanupDb() {
        DatabaseManager.closeConnection();
        try { Files.deleteIfExists(Path.of(DB_PATH)); } catch (Exception ignore) {}
    }

    // --- Unit tests for the plain POJO behaviour ---

    @Test
    void unit_constructorAndGettersReturnValues() {
        User u = new User(42, "alice", "secret", "Alice Doe");
        assertEquals(42, u.getId());
        assertEquals("alice", u.getUsername());
        assertEquals("secret", u.getPassword());
        assertEquals("Alice Doe", u.getName());
    }

    @Test
    void unit_settersUpdateFields() {
        User u = new User(0, "u", "p", "n");
        u.setId(7);
        u.setUsername("bob");
        u.setPassword("pw");
        u.setName("Bob B");
        assertEquals(7, u.getId());
        assertEquals("bob", u.getUsername());
        assertEquals("pw", u.getPassword());
        assertEquals("Bob B", u.getName());
    }

    @Test
    void unit_acceptsNullAndEmptyValues() {
        User u = new User(0, null, null, null);
        assertNull(u.getUsername());
        assertNull(u.getPassword());
        assertNull(u.getName());

        u.setUsername("");
        u.setPassword("");
        u.setName("");
        assertEquals("", u.getUsername());
        assertEquals("", u.getPassword());
        assertEquals("", u.getName());
    }

    // --- Integration tests using DatabaseManager (falls vorhanden) ---

    @Test
    void integ_createAndListUser_viaDatabaseManager() throws Exception {
        boolean created = DatabaseManager.createOrUpdateUser("integ_user", "pw123", "Integ User");
        assertTrue(created, "Erwartet: createOrUpdateUser gibt true zurück");

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.stream().anyMatch(u -> "integ_user".equalsIgnoreCase(u.username)));
        DatabaseManager.UserInfo found = users.stream().filter(u -> "integ_user".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(found);
        assertEquals("Integ User", found.name);
    }

    @Test
    void integ_updateUser_changesNameAndKeepsUsername() throws Exception {
        assertTrue(DatabaseManager.createOrUpdateUser("up_user", "pw1", "First Name"));
        // update same username with new name/password
        assertTrue(DatabaseManager.createOrUpdateUser("up_user", "pw2", "Updated Name"));

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        DatabaseManager.UserInfo u = users.stream().filter(x -> "up_user".equalsIgnoreCase(x.username)).findFirst().orElse(null);
        assertNotNull(u);
        assertEquals("Updated Name", u.name);
    }

    @Test
    void integ_invalidCreateOrUpdateUser_returnsFalseForBadInput() {
        // These calls should not create users and should return false (implementation-dependent but commonly expected)
        assertFalse(DatabaseManager.createOrUpdateUser(null, "pw", "Name"));
        assertFalse(DatabaseManager.createOrUpdateUser("", "pw", "Name"));
        assertFalse(DatabaseManager.createOrUpdateUser("   ", "pw", "Name"));
    }
}