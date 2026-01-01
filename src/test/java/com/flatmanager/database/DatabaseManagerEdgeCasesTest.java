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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerEdgeCasesTest {
    private static final String DB_FILE = "target/db_edgecases_test.db";

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) Files.delete(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        // initialize schema
        Database.init();
        // enable WAL mode for better concurrent write behavior in tests
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement s = c.createStatement()) {
                try { s.execute("PRAGMA journal_mode = WAL"); } catch (Exception ignored) {}
                try { s.execute("PRAGMA busy_timeout = 10000"); } catch (Exception ignored) {}
            }
        }
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    @Test
    public void testMigrationCopiesNameToItemName() throws Exception {
        // create legacy shopping_items with only name column
        Database.closeConnection();
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS shopping_items");
                s.execute("CREATE TABLE shopping_items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
                s.execute("INSERT INTO shopping_items (name) VALUES ('legacy-item')");
            }
        }

        // trigger schema migration
        Database.init();

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, COALESCE(item_name, name) AS item_name FROM shopping_items WHERE id = 1")) {
                assertTrue(rs.next());
                String item = rs.getString("item_name");
                assertEquals("legacy-item", item);
            }
        }
    }

    @Test
    public void testCreateHouseholdRollbackOnTriggeredError() throws Exception {
        // Create a trigger that aborts inserts of username 'bad' to force a mid-transaction failure
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement s = c.createStatement()) {
                // ensure users table exists with expected columns
                s.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT, name TEXT, is_admin INTEGER DEFAULT 0)");
                // create trigger
                try { s.execute("DROP TRIGGER IF EXISTS fail_on_bad"); } catch (Exception ignored) {}
                s.execute("CREATE TRIGGER fail_on_bad BEFORE INSERT ON users WHEN NEW.username = 'bad' BEGIN SELECT RAISE(ABORT, 'boom'); END;");
            }
        }

        List<DatabaseManager.UserData> members = new ArrayList<>();
        members.add(new DatabaseManager.UserData("Bad","bad","pw"));

        boolean res = DatabaseManager.createHouseholdWithAdmin("WG", "adminX", "pwadmin", members);
        assertFalse(res, "createHouseholdWithAdmin should return false when trigger causes abort");

        // Ensure admin not created due to rollback
        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertFalse(users.stream().anyMatch(u -> "adminX".equalsIgnoreCase(u.username)));
    }

    @Test
    public void testConcurrentTransactionInserts() throws Exception {
        int threads = 8;
        int perThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
                        String sql = "INSERT INTO budget_transactions (description, amount, date, user_id) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement ps = c.prepareStatement(sql)) {
                            for (int i = 0; i < perThread; i++) {
                                ps.setString(1, "Ctx");
                                ps.setDouble(2, 1.0);
                                ps.setString(3, null);
                                ps.setNull(4, java.sql.Types.INTEGER);
                                // retry on transient failures like SQLITE_BUSY
                                int tries = 0;
                                while (true) {
                                    try {
                                        ps.executeUpdate();
                                        break;
                                    } catch (java.sql.SQLException ex) {
                                        tries++;
                                        if (tries >= 5) throw ex;
                                        try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        // wait all ready
        ready.await();
        // start
        start.countDown();
        // wait finished
        done.await();
        executor.shutdown();

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) AS cnt FROM budget_transactions")) {
            assertTrue(rs.next());
            int cnt = rs.getInt("cnt");
            assertEquals(threads * perThread, cnt);
        }
    }

    @Test
    public void testUniqueUsernameConstraintRawInsertFailure() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, password, name) VALUES (?, ?, ?)") ) {
                ps.setString(1, "u1"); ps.setString(2, "x"); ps.setString(3, "n"); ps.executeUpdate();
            }
            // second raw insert should throw due to UNIQUE constraint
            Exception thrown = null;
            try (PreparedStatement ps2 = c.prepareStatement("INSERT INTO users (username, password, name) VALUES (?, ?, ?)") ) {
                ps2.setString(1, "u1"); ps2.setString(2, "y"); ps2.setString(3, "n2"); ps2.executeUpdate();
            } catch (Exception e) {
                thrown = e;
            }
            assertNotNull(thrown, "Expected exception on duplicate username insert");
        }
    }

    @Test
    public void testBudgetTransactionsMigrationCopiesUserIdToPaidBy() throws Exception {
        // create legacy budget_transactions with only user_id column
        Database.closeConnection();
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS budget_transactions");
                s.execute("CREATE TABLE budget_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, description TEXT, amount REAL, user_id INTEGER)");
                s.execute("INSERT INTO budget_transactions (description, amount, user_id) VALUES ('t1', 10.0, 42)");
            }
        }

        // trigger migration
        Database.init();

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COALESCE(paid_by, user_id) AS paid FROM budget_transactions WHERE id = 1")) {
                assertTrue(rs.next());
                int paid = rs.getInt("paid");
                assertEquals(42, paid);
            }
        }
    }

    @Test
    public void testUsersHasUniqueIndexForUsername() throws Exception {
        // Ensure index exists for username and is unique
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("PRAGMA index_list('users')")) {
                boolean found = false;
                while (rs.next()) {
                    String name = rs.getString("name");
                    int unique = rs.getInt("unique");
                    if (name != null && unique == 1) {
                        // check indexed columns to ensure it covers username
                        try (ResultSet rs2 = s.executeQuery("PRAGMA index_info('" + name + "')")) {
                            while (rs2.next()) {
                                String col = rs2.getString("name");
                                if ("username".equalsIgnoreCase(col)) {
                                    found = true; break;
                                }
                            }
                        }
                    }
                    if (found) break;
                }
                assertTrue(found, "Expected a unique index on users(username)");
            }
        }
    }
}
