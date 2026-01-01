package com.flatmanager.database;

import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerDbTest {
    private static final String DB_FILE = "target/db_manager_test.db";

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) Files.delete(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        // ensure schema is created
        Database.init();
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    @Test
    public void testEnsureSchemaCreatesTables() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('users','shopping_items','budget_transactions') ORDER BY name")) {
                int count = 0;
                while (rs.next()) {
                    String name = rs.getString("name");
                    assertNotNull(name);
                    count++;
                }
                assertEquals(3, count, "Expected core tables to exist");
            }
        }
    }

    @Test
    public void testCreateOrUpdateUserAndListUsers() throws Exception {
        boolean ok = DatabaseManager.createOrUpdateUser("testuser", "pw123", "Test User");
        assertTrue(ok);

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.stream().anyMatch(u -> "testuser".equalsIgnoreCase(u.username)));

        // update
        boolean ok2 = DatabaseManager.createOrUpdateUser("testuser", "pw456", "Test User New");
        assertTrue(ok2);

        List<DatabaseManager.UserInfo> users2 = DatabaseManager.listUsers();
        assertTrue(users2.stream().anyMatch(u -> "testuser".equalsIgnoreCase(u.username) && "Test User New".equals(u.name)));

        // passwordHash should be masked (not null)
        DatabaseManager.UserInfo info = users2.stream().filter(u -> "testuser".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(info);
        assertNotNull(info.passwordHash);
        assertTrue(info.passwordHash.contains("...") || info.passwordHash.equals("****"));
    }

    @Test
    public void testAddOrUpdateTransactionAndDelete() throws Exception {
        DatabaseManager.Transaction t = new DatabaseManager.Transaction(0, "Lunch", 12.5, LocalDate.now().toString(), null, null);
        assertTrue(DatabaseManager.addOrUpdateTransaction(t));

        List<DatabaseManager.Transaction> txs = DatabaseManager.listTransactions();
        assertTrue(txs.stream().anyMatch(x -> "Lunch".equals(x.description) && Math.abs(x.amount - 12.5) < 0.001));

        int id = txs.stream().filter(x -> "Lunch".equals(x.description)).findFirst().map(x -> x.id).orElse(-1);
        assertTrue(id > 0);

        assertTrue(DatabaseManager.deleteTransaction(id));
        List<DatabaseManager.Transaction> txs2 = DatabaseManager.listTransactions();
        assertFalse(txs2.stream().anyMatch(x -> x.id == id));
    }

    @Test
    public void testAddOrUpdateShoppingItemAndDelete() throws Exception {
        DatabaseManager.ShoppingItem it = new DatabaseManager.ShoppingItem(0, "Milk", 1, false, null, null, LocalDate.now().toString());
        assertTrue(DatabaseManager.addOrUpdateShoppingItem(it));

        List<DatabaseManager.ShoppingItem> items = DatabaseManager.listShoppingItems();
        assertTrue(items.stream().anyMatch(i -> "Milk".equalsIgnoreCase(i.name)));

        int id = items.stream().filter(i -> "Milk".equalsIgnoreCase(i.name)).findFirst().map(i -> i.id).orElse(-1);
        assertTrue(id > 0);

        assertTrue(DatabaseManager.deleteShoppingItem(id));
        List<DatabaseManager.ShoppingItem> items2 = DatabaseManager.listShoppingItems();
        assertFalse(items2.stream().anyMatch(i -> i.id == id));
    }
}

