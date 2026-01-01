package com.flatmanager.database;

import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

public class DeleteUserAdminProtectionTest {
    private static final String DB_FILE = "target/delete_user_admin_test.db";

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) Files.delete(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        Database.init();
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    @Test
    public void testPreventDeleteLastAdmin() throws Exception {
        assertTrue(DatabaseManager.createHouseholdWithAdmin(null, "onlyadmin", "pw", null));

        // attempt to delete the only admin -> should be prevented
        assertFalse(DatabaseManager.deleteUser("onlyadmin"));

        // ensure user still exists
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = c.prepareStatement("SELECT username FROM users WHERE username = ? COLLATE NOCASE")) {
            ps.setString(1, "onlyadmin");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
            }
        }
    }

    @Test
    public void testAllowDeleteWhenMultipleAdmins() throws Exception {
        assertTrue(DatabaseManager.createHouseholdWithAdmin(null, "admin1", "pw", null));
        assertTrue(DatabaseManager.createOrUpdateUser("admin2", "pw", "Admin Two"));

        // make admin2 an admin via direct SQL
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = c.prepareStatement("UPDATE users SET is_admin = 1 WHERE username = ? COLLATE NOCASE")) {
            ps.setString(1, "admin2");
            ps.executeUpdate();
        }

        // now deleting admin1 should be allowed
        assertTrue(DatabaseManager.deleteUser("admin1"));

        // admin1 removed, admin2 remains and is admin
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "admin1");
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next(), "admin1 should be deleted");
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT username, COALESCE(is_admin,0) AS is_admin FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "admin2");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("is_admin"));
                }
            }
        }
    }

    @Test
    public void testDeleteNonAdminAllowed() throws Exception {
        assertTrue(DatabaseManager.createHouseholdWithAdmin(null, "theadmin", "pw", null));
        assertTrue(DatabaseManager.createOrUpdateUser("member", "pw", "Member"));

        assertTrue(DatabaseManager.deleteUser("member"));

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
            ps.setString(1, "member");
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next(), "member should be deleted");
            }
        }

        // admin should still exist
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
            ps.setString(1, "theadmin");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "admin should still exist");
            }
        }
    }
}

