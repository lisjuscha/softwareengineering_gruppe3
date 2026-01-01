package com.flatmanager.database;

import com.flatmanager.dao.CleaningTaskDao;
import com.flatmanager.model.CleaningTask;
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
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class DeleteUserDataIntegrityTest {
    private static final String DB_FILE = "target/delete_user_test.db";

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
    public void testDeleteUserNullsReferencesAndRemovesUser() throws Exception {
        // create users
        assertTrue(DatabaseManager.createOrUpdateUser("john", "pw", "John"));
        assertTrue(DatabaseManager.createOrUpdateUser("jane", "pw", "Jane"));

        // get john id and jane id
        Integer johnId = null, janeId = null;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, username FROM users WHERE username IN ('john','jane')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String u = rs.getString("username");
                        if ("john".equalsIgnoreCase(u)) johnId = id;
                        if ("jane".equalsIgnoreCase(u)) janeId = id;
                    }
                }
            }
        }
        assertNotNull(johnId);
        assertNotNull(janeId);

        // insert a shopping_item with added_by = johnId
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO shopping_items (item_name, quantity, added_by, created_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, "Milk");
                ps.setInt(2, 1);
                ps.setInt(3, johnId);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate();
            }
        }

        // insert a budget_transaction referencing john as user_id and paid_by
        DatabaseManager.Transaction t = new DatabaseManager.Transaction(0, "Groceries", 12.5, LocalDate.now().toString(), johnId, johnId);
        assertTrue(DatabaseManager.addOrUpdateTransaction(t));

        // create cleaning_tasks table and insert a task assigned to john
        CleaningTaskDao ctd = new CleaningTaskDao();
        ctd.init();
        CleaningTask task = new CleaningTask("Wash dishes", LocalDate.now(), "john", "", false);
        ctd.insert(task);

        // Sanity checks: references exist
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT added_by FROM shopping_items WHERE item_name = ?")) {
                ps.setString(1, "Milk");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int ab = rs.getInt("added_by");
                    assertFalse(rs.wasNull());
                    assertEquals(johnId.intValue(), ab);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT user_id, paid_by FROM budget_transactions WHERE description = ?")) {
                ps.setString(1, "Groceries");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int uid = rs.getInt("user_id");
                    int pb = rs.getInt("paid_by");
                    assertFalse(rs.wasNull());
                    assertEquals(johnId.intValue(), uid);
                    assertEquals(johnId.intValue(), pb);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT assigned_to FROM cleaning_tasks WHERE title = ?")) {
                ps.setString(1, "Wash dishes");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String at = rs.getString("assigned_to");
                    assertEquals("john", at);
                }
            }
        }

        // Call deleteUser
        assertTrue(DatabaseManager.deleteUser("john"));

        // After deletion: user not present and references cleared
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "john");
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next(), "User john should be deleted");
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT added_by FROM shopping_items WHERE item_name = ?")) {
                ps.setString(1, "Milk");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int ab = rs.getInt("added_by");
                    assertTrue(rs.wasNull(), "added_by should be NULL after user deletion");
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT user_id, paid_by FROM budget_transactions WHERE description = ?")) {
                ps.setString(1, "Groceries");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int uid = rs.getInt("user_id");
                    assertTrue(rs.wasNull(), "user_id should be NULL after user deletion");
                    int pb = rs.getInt("paid_by");
                    assertTrue(rs.wasNull(), "paid_by should be NULL after user deletion");
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT assigned_to FROM cleaning_tasks WHERE title = ?")) {
                ps.setString(1, "Wash dishes");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String at = rs.getString("assigned_to");
                    assertNull(at, "assigned_to should be NULL after user deletion");
                }
            }

            // Ensure other user still exists
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "jane");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                }
            }
        }
    }
}

