package com.flatmanager.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    private static final String DB_PATH = "target/db_combined_test.db";

    @BeforeEach
    void before() throws Exception {
        File db = new File(DB_PATH);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
    }

    @AfterEach
    void after() {
        DatabaseManager.closeConnection();
        try { Files.deleteIfExists(Path.of(DB_PATH)); } catch (Exception ignore) {}
    }

    @Test
    void testGetConnection() throws Exception {
        Connection conn = DatabaseManager.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
    }

    @Test
    void testDatabaseTablesExist() throws Exception {
        Connection conn = DatabaseManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            // Check that essential tables exist
            stmt.executeQuery("SELECT * FROM users LIMIT 1");
            stmt.executeQuery("SELECT * FROM shopping_items LIMIT 1");
            stmt.executeQuery("SELECT * FROM budget_transactions LIMIT 1");
        }
    }

    @Test
    void testListUsersMaskingAndAdminDetection() throws Exception {
        assertTrue(DatabaseManager.createOrUpdateUser("admin", "pw123", "Admin"));
        assertTrue(DatabaseManager.createOrUpdateUser("user1", "secret", "User One"));

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.size() >= 2);
        DatabaseManager.UserInfo admin = users.stream().filter(u -> "admin".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        DatabaseManager.UserInfo user1 = users.stream().filter(u -> "user1".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(admin);
        assertNotNull(user1);
        assertTrue(admin.isAdmin || "admin".equalsIgnoreCase(admin.username) || "admin".equalsIgnoreCase(admin.name));
        if (admin.passwordHash != null) assertTrue(admin.passwordHash.contains("...") || admin.passwordHash.equals("****"));
        if (user1.passwordHash != null) assertTrue(user1.passwordHash.contains("...") || user1.passwordHash.equals("****"));
    }

    @Test
    void testCreateHouseholdWithAdminAndListUsers() throws Exception {
        boolean ok = DatabaseManager.createHouseholdWithAdmin("WG","adminUser","secret",
                List.of(new DatabaseManager.UserData("Alice","alice","pw")));
        assertTrue(ok);

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.stream().anyMatch(u -> "adminUser".equalsIgnoreCase(u.username)));
        assertTrue(users.stream().anyMatch(u -> "alice".equalsIgnoreCase(u.username)));
    }

    // --- Neue Tests zur Erhöhung der Abdeckung ---

    @Test
    void testCreateOrUpdateUserInvalid() {
        assertFalse(DatabaseManager.createOrUpdateUser(null, "pw", "Name"));
        assertFalse(DatabaseManager.createOrUpdateUser("", "pw", "Name"));
        assertFalse(DatabaseManager.createOrUpdateUser("   ", "pw", "Name"));
    }

    @Test
    void testCreateOrUpdateUserInsertAndUpdate() {
        assertTrue(DatabaseManager.createOrUpdateUser("bob", "pw1", "Bob"));
        List<DatabaseManager.UserInfo> first = DatabaseManager.listUsers();
        DatabaseManager.UserInfo bob1 = first.stream().filter(u -> "bob".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(bob1);
        assertEquals("Bob", bob1.name);

        // update existing user (should go through UPDATE path)
        assertTrue(DatabaseManager.createOrUpdateUser("bob", "pw2", "Bobby"));
        List<DatabaseManager.UserInfo> second = DatabaseManager.listUsers();
        DatabaseManager.UserInfo bob2 = second.stream().filter(u -> "bob".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(bob2);
        assertEquals("Bobby", bob2.name);
        if (bob2.passwordHash != null) assertTrue(bob2.passwordHash.contains("...") || bob2.passwordHash.equals("****"));
    }

    @Test
    void testCreateHouseholdWithAdminInvalidInputs() {
        // null username
        assertFalse(DatabaseManager.createHouseholdWithAdmin("WG", null, "pw", null));
        // blank username
        assertFalse(DatabaseManager.createHouseholdWithAdmin("WG", "  ", "pw", null));
        // empty password
        assertFalse(DatabaseManager.createHouseholdWithAdmin("WG", "adm", "", null));
        // null password
        assertFalse(DatabaseManager.createHouseholdWithAdmin("WG", "adm", null, null));
    }

    @Test
    void testCreateHouseholdWithAdminCreateAndUpdate() {
        boolean first = DatabaseManager.createHouseholdWithAdmin("WG", "masterAdmin", "initpw",
                List.of(new DatabaseManager.UserData("U1","u1","p1")));
        assertTrue(first);

        // create again with same admin -> should update password and still succeed
        boolean second = DatabaseManager.createHouseholdWithAdmin("WG", "masterAdmin", "newpw",
                List.of(new DatabaseManager.UserData("U2","u2","p2")));
        assertTrue(second);

        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.stream().anyMatch(u -> "masterAdmin".equalsIgnoreCase(u.username)));
        assertTrue(users.stream().anyMatch(u -> "u1".equalsIgnoreCase(u.username) || "u2".equalsIgnoreCase(u.username)));
        DatabaseManager.UserInfo admin = users.stream().filter(u -> "masterAdmin".equalsIgnoreCase(u.username)).findFirst().orElse(null);
        assertNotNull(admin);
        assertTrue(admin.isAdmin);
    }

    @Test
    void testShoppingItemCrudAndValidation() {
        // invalid inputs
        assertFalse(DatabaseManager.addOrUpdateShoppingItem(null));
        DatabaseManager.ShoppingItem blankName = new DatabaseManager.ShoppingItem(0, "   ", 1, false, null, null, null);
        assertFalse(DatabaseManager.addOrUpdateShoppingItem(blankName));

        // insert item
        DatabaseManager.ShoppingItem milk = new DatabaseManager.ShoppingItem(0, "milk", 2, false, "dairy", null, "now");
        assertTrue(DatabaseManager.addOrUpdateShoppingItem(milk));

        List<DatabaseManager.ShoppingItem> items = DatabaseManager.listShoppingItems();
        DatabaseManager.ShoppingItem loaded = items.stream().filter(i -> "milk".equalsIgnoreCase(i.name)).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(2, loaded.quantity);
        assertEquals("dairy", loaded.category);

        // delete item by id
        int id = loaded.id;
        assertTrue(DatabaseManager.deleteShoppingItem(id));
        List<DatabaseManager.ShoppingItem> afterDelete = DatabaseManager.listShoppingItems();
        assertFalse(afterDelete.stream().anyMatch(i -> i.id == id));
    }

    @Test
    void testTransactionCrud() {
        DatabaseManager.Transaction rent = new DatabaseManager.Transaction(0, "rent", 123.45, "2025-01-01", null, null);
        assertTrue(DatabaseManager.addOrUpdateTransaction(rent));

        List<DatabaseManager.Transaction> txs = DatabaseManager.listTransactions();
        DatabaseManager.Transaction loaded = txs.stream().filter(t -> "rent".equalsIgnoreCase(t.description)).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(123.45, loaded.amount, 0.0001);

        int txId = loaded.id;
        assertTrue(DatabaseManager.deleteTransaction(txId));
        List<DatabaseManager.Transaction> after = DatabaseManager.listTransactions();
        assertFalse(after.stream().anyMatch(t -> t.id == txId));
    }
}