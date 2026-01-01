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
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class DBTransactionRollbackTest {
    private static final String DB_FILE = "target/db_transaction_rollback_test.db";

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
    public void testCreateHouseholdWithAdminRollsBackOnMemberInsertFailure() throws Exception {
        // Create a trigger that aborts any INSERT into users where username = 'bad'
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             Statement s = c.createStatement()) {
            s.execute("CREATE TRIGGER fail_insert_bad BEFORE INSERT ON users WHEN NEW.username = 'bad' BEGIN SELECT RAISE(ABORT, 'triggered'); END;");
        }

        // Call createHouseholdWithAdmin with a member that will trigger the failure
        DatabaseManager.UserData bad = new DatabaseManager.UserData("Bad", "bad", "pw");
        boolean ok = DatabaseManager.createHouseholdWithAdmin(null, "admin", "adminpw", Arrays.asList(bad));
        assertFalse(ok, "createHouseholdWithAdmin should fail when member insert triggers an error and rollback occurs");

        // Ensure that no user 'admin' or 'bad' exists (transaction rolled back)
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = c.prepareStatement("SELECT username FROM users WHERE username IN ('admin','bad')")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next(), "No users should exist after rollback");
            }
        }
    }

    @Test
    public void testDeleteUserRollsBackOnDeleteFailure() throws Exception {
        // Create a user 'victim' and a dependent shopping_item referencing them
        assertTrue(DatabaseManager.createOrUpdateUser("victim", "pw", "Victim"));
        Integer vid = null;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "victim");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) vid = rs.getInt(1);
                }
            }
            assertNotNull(vid);
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO shopping_items (item_name, quantity, added_by, created_at) VALUES (?, ?, ?, datetime('now'))")) {
                ins.setString(1, "ItemX");
                ins.setInt(2, 1);
                ins.setInt(3, vid);
                ins.executeUpdate();
            }
        }

        // Create a trigger that aborts deleting the victim
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             Statement s = c.createStatement()) {
            s.execute("CREATE TRIGGER fail_delete_victim BEFORE DELETE ON users WHEN OLD.username = 'victim' BEGIN SELECT RAISE(ABORT, 'cannot delete'); END;");
        }

        // Attempt deletion; should return false and perform rollback
        boolean res = DatabaseManager.deleteUser("victim");
        assertFalse(res, "deleteUser should return false when DELETE triggers an error and rollback occurs");

        // Ensure user still exists and shopping_item still references them
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "victim");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "victim should still exist after rollback");
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT added_by FROM shopping_items WHERE item_name = ?")) {
                ps.setString(1, "ItemX");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int ab = rs.getInt(1);
                    assertFalse(rs.wasNull());
                    assertEquals(vid.intValue(), ab, "shopping_item.added_by should still reference victim after rollback");
                }
            }
        }
    }
}

