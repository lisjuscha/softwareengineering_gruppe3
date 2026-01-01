package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetViewTest {

    private static final String DB_FILE = "target/budget_view_test.db";

    @BeforeAll
    public static void initToolkit() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignore) {}
    }

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) java.nio.file.Files.deleteIfExists(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        DatabaseManager.closeConnection();

        // create minimal schema required
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS users (username TEXT UNIQUE)");
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS budget_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, description TEXT, amount REAL, paid_by TEXT, date TEXT, category TEXT)");
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS budget_shares (id INTEGER PRIMARY KEY AUTOINCREMENT, transaction_id INTEGER, username TEXT, share REAL)");
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO users (username) VALUES (?)")) {
                ps.setString(1, "Alice"); ps.executeUpdate();
                ps.setString(1, "Bob"); ps.executeUpdate();
            }
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        Database.closeConnection();
        DatabaseManager.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignored) {}
    }

    @Test
    public void testComputeBalancesAndDebts() throws Exception {
        // Insert one transaction: Alice paid 100, shares 0.5/0.5
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, ?, ?, ?)") ) {
                p.setString(1, "Einkauf"); p.setDouble(2, 100.0); p.setString(3, "Alice"); p.setString(4, "2026-01-01"); p.setString(5, "Einkäufe");
                p.executeUpdate();
            }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO budget_shares (transaction_id, username, share) VALUES (?, ?, ?)") ) {
                        ps.setInt(1, id); ps.setString(2, "Alice"); ps.setDouble(3, 0.5); ps.executeUpdate();
                        ps.setInt(1, id); ps.setString(2, "Bob"); ps.setDouble(3, 0.5); ps.executeUpdate();
                    }
                }
            }
        }

        BudgetView bv = new BudgetView("Alice");

        // call private computeBalances via reflection
        Method computeBalances = BudgetView.class.getDeclaredMethod("computeBalances");
        computeBalances.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Double> balances = (Map<String, Double>) computeBalances.invoke(bv);

        assertNotNull(balances);
        // Alice: +100 -50 = +50 ; Bob: -50
        assertEquals(50.0, Math.round(balances.getOrDefault("Alice", 0.0) * 100.0) / 100.0);
        assertEquals(-50.0, Math.round(balances.getOrDefault("Bob", 0.0) * 100.0) / 100.0);

        // compute pairwise debts
        Method computePairwise = BudgetView.class.getDeclaredMethod("computePairwiseDebts", Map.class);
        computePairwise.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> debts = (List<String>) computePairwise.invoke(bv, balances);
        assertNotNull(debts);
        assertFalse(debts.isEmpty());
        // should mention Bob → Alice
        boolean found = debts.stream().anyMatch(s -> s.contains("Bob") && s.contains("Alice") && s.contains("+") );
        assertTrue(found, "Erwarte eine Schuldzuweisung von Bob an Alice");
    }

    @Test
    public void testAddAndDeleteTransactionWritesAndRemovesDbRows() throws Exception {
        BudgetView bv = new BudgetView("Alice");

        Method addTransaction = BudgetView.class.getDeclaredMethod("addTransaction", String.class, double.class, String.class, String.class, String.class, List.class);
        addTransaction.setAccessible(true);

        int newId = (int) addTransaction.invoke(bv, "Taxi", 20.0, "Bob", "2026-01-02", "Sonstiges", List.of("Bob"));
        assertTrue(newId > 0);

        // verify DB rows exist
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) as cnt FROM budget_transactions WHERE id = ?")) {
                ps.setInt(1, newId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(1, rs.getInt("cnt")); }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) as cnt FROM budget_shares WHERE transaction_id = ?")) {
                ps.setInt(1, newId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(1, rs.getInt("cnt")); }
            }
        }

        // delete
        Method deleteTransaction = BudgetView.class.getDeclaredMethod("deleteTransaction", int.class);
        deleteTransaction.setAccessible(true);
        deleteTransaction.invoke(bv, newId);

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) as cnt FROM budget_transactions WHERE id = ?")) {
                ps.setInt(1, newId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(0, rs.getInt("cnt")); }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) as cnt FROM budget_shares WHERE transaction_id = ?")) {
                ps.setInt(1, newId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(0, rs.getInt("cnt")); }
            }
        }
    }

    @Test
    public void testEnsureSharesTableExistsCreatesTable() throws Exception {
        // drop table and then create BudgetView which should recreate it
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try { c.createStatement().execute("DROP TABLE IF EXISTS budget_shares"); } catch (Exception ignore) {}
        }

        new BudgetView("Alice");

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='budget_shares'")) {
                assertTrue(rs.next());
            }
        }
    }

    @Test
    public void testTotalLabelReflectsSum() throws Exception {
        // insert two transactions
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, ?, ?, ?)") ) {
                p.setString(1, "T1"); p.setDouble(2, 10.0); p.setString(3, "Alice"); p.setString(4, "2026-01-01"); p.setString(5, "Einkäufe"); p.executeUpdate();
                p.setString(1, "T2"); p.setDouble(2, 5.5); p.setString(3, "Bob"); p.setString(4, "2026-01-02"); p.setString(5, "Sonstiges"); p.executeUpdate();
            }
        }

        BudgetView bv = new BudgetView("Alice");
        // totalLabel is private field
        Field totalLabelF = BudgetView.class.getDeclaredField("totalLabel");
        totalLabelF.setAccessible(true);
        Label totalLabel = (Label) totalLabelF.get(bv);
        assertNotNull(totalLabel);

        // call updateTotal to ensure labels updated
        Method updateTotal = BudgetView.class.getDeclaredMethod("updateTotal");
        updateTotal.setAccessible(true);
        updateTotal.invoke(bv);

        String text = totalLabel.getText();
        // Should contain sum 15.5 in German locale format (15,50 €)
        assertTrue(text.contains("15"), "TOTAL label should contain the aggregated sum");
    }

    @Test
    public void testAddNegativeAndZeroAmountStored() throws Exception {
        BudgetView bv = new BudgetView("Alice");

        Method addTransaction = BudgetView.class.getDeclaredMethod("addTransaction", String.class, double.class, String.class, String.class, String.class, List.class);
        addTransaction.setAccessible(true);

        int negId = (int) addTransaction.invoke(bv, "Korrektur", -30.0, "Alice", "2026-01-03", "Sonstiges", List.of("Alice"));
        assertTrue(negId > 0);
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT amount FROM budget_transactions WHERE id = ?")) {
                ps.setInt(1, negId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(-30.0, rs.getDouble("amount"), 0.0001); }
            }
        }

        int zeroId = (int) addTransaction.invoke(bv, "Gratis", 0.0, "Bob", "2026-01-04", "Sonstiges", List.of("Bob"));
        assertTrue(zeroId > 0);
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT amount FROM budget_transactions WHERE id = ?")) {
                ps.setInt(1, zeroId);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(0.0, rs.getDouble("amount"), 0.0001); }
            }
        }
    }

    @Test
    public void testManyParticipantsSplitSharesSumToAmount() throws Exception {
        // create additional users
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO users (username) VALUES (?)")) {
                ps.setString(1, "C1"); ps.executeUpdate();
                ps.setString(1, "C2"); ps.executeUpdate();
            }
        }

        BudgetView bv = new BudgetView("Alice");
        Method addTransaction = BudgetView.class.getDeclaredMethod("addTransaction", String.class, double.class, String.class, String.class, String.class, List.class);
        addTransaction.setAccessible(true);

        List<String> parts = List.of("Alice", "Bob", "C1", "C2");
        int id = (int) addTransaction.invoke(bv, "Event", 100.0, "Alice", "2026-01-05", "Aktivitäten", parts);
        assertTrue(id > 0);

        // sum of shares * amount should equal amount
        double sum = 0.0;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT username, share FROM budget_shares WHERE transaction_id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        double share = rs.getDouble("share");
                        sum += share * 100.0;
                        count++;
                    }
                    assertEquals(parts.size(), count);
                }
            }
        }
        assertEquals(100.0, Math.round(sum * 100.0) / 100.0);
    }

    @Test
    public void testAddWithoutSharesDefaultsDistributionInComputeBalances() throws Exception {
        // Ensure users: Alice and Bob exist (setup does)
        BudgetView bv = new BudgetView("Alice");
        Method addTransaction = BudgetView.class.getDeclaredMethod("addTransaction", String.class, double.class, String.class, String.class, String.class, List.class);
        addTransaction.setAccessible(true);

        int id = (int) addTransaction.invoke(bv, "SharedNoShares", 80.0, "Bob", "2026-01-06", "Einkäufe", null);
        assertTrue(id > 0);

        // load transactions into memory so computeBalances sees the new entry
        Method loadTransactions = BudgetView.class.getDeclaredMethod("loadTransactions");
        loadTransactions.setAccessible(true);
        loadTransactions.invoke(bv);

        // compute balances should consider both users (Alice,Bob) and distribute
        Method computeBalances = BudgetView.class.getDeclaredMethod("computeBalances");
        computeBalances.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Double> balances = (Map<String, Double>) computeBalances.invoke(bv);
        // Bob paid 80, each participant share is 40 => Bob: +80 -40 = +40; Alice: -40
        assertEquals(40.0, Math.round(balances.getOrDefault("Bob", 0.0) * 100.0) / 100.0);
        assertEquals(-40.0, Math.round(balances.getOrDefault("Alice", 0.0) * 100.0) / 100.0);
    }

    @Test
    public void testLargeAmountPrecisionStoredAndSummed() throws Exception {
        BudgetView bv = new BudgetView("Alice");
        Method addTransaction = BudgetView.class.getDeclaredMethod("addTransaction", String.class, double.class, String.class, String.class, String.class, List.class);
        addTransaction.setAccessible(true);

        double large = 1_234_567_890.12;
        int id = (int) addTransaction.invoke(bv, "Big", large, "Alice", "2026-01-07", "Sonstiges", List.of("Alice"));
        assertTrue(id > 0);

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT amount FROM budget_transactions WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(large, rs.getDouble("amount"), 0.01); }
            }
        }
    }

    @Test
    public void testSharesSumNotOneHandledGracefully() throws Exception {
        // Insert transaction with shares that don't sum to 1 (e.g., 0.3 + 0.3 = 0.6)
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, ?, ?, ?)") ) {
                p.setString(1, "BrokenShares"); p.setDouble(2, 100.0); p.setString(3, "Alice"); p.setString(4, "2026-01-08"); p.setString(5, "Sonstiges");
                p.executeUpdate();
            }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO budget_shares (transaction_id, username, share) VALUES (?, ?, ?)")) {
                        ps.setInt(1, id); ps.setString(2, "Alice"); ps.setDouble(3, 0.3); ps.executeUpdate();
                        ps.setInt(1, id); ps.setString(2, "Bob"); ps.setDouble(3, 0.3); ps.executeUpdate();
                    }
                }
            }
        }

        BudgetView bv = new BudgetView("Alice");
        Method computeBalances = BudgetView.class.getDeclaredMethod("computeBalances");
        computeBalances.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Double> balances = (Map<String, Double>) computeBalances.invoke(bv);
        // Even if shares sum != 1, code uses exact shares => Bob and Alice debits should reflect 30 and 30
        assertEquals(70.0, Math.round(balances.getOrDefault("Alice", 0.0) * 100.0) / 100.0);
        assertEquals(-30.0, Math.round(balances.getOrDefault("Bob", 0.0) * 100.0) / 100.0);
    }

    @Test
    public void testTransactionWithNullPaidByUsesDefaultParticipants() throws Exception {
        // Insert transaction with paid_by = NULL and no shares
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, NULL, ?, ?)") ) {
                p.setString(1, "Anon"); p.setDouble(2, 60.0); p.setString(3, "2026-01-09"); p.setString(4, "Sonstiges"); p.executeUpdate();
            }
        }

        BudgetView bv = new BudgetView("Alice");
        Method computeBalances = BudgetView.class.getDeclaredMethod("computeBalances");
        computeBalances.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Double> balances = (Map<String, Double>) computeBalances.invoke(bv);

        // With default participants [Alice,Bob], each should be -30 (no payer to add amount)
        assertEquals(-30.0, Math.round(balances.getOrDefault("Alice", 0.0) * 100.0) / 100.0);
        assertEquals(-30.0, Math.round(balances.getOrDefault("Bob", 0.0) * 100.0) / 100.0);
    }

    @Test
    public void testComputePairwiseDebtsWhenBalancesZero() throws Exception {
        BudgetView bv = new BudgetView("Alice");
        // empty transactions -> balances empty
        Method computePairwise = BudgetView.class.getDeclaredMethod("computePairwiseDebts", Map.class);
        computePairwise.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) computePairwise.invoke(bv, Map.of());
        assertNotNull(result);
        assertEquals(0, result.size());

        // if balances present but all near zero, expect 'Keine offenen Schulden'
        Method computeBalances = BudgetView.class.getDeclaredMethod("computeBalances");
        computeBalances.setAccessible(true);
        // simulate balances with tiny numbers
        Map<String, Double> tiny = Map.of("A", 0.00001, "B", -0.00001);
        @SuppressWarnings("unchecked")
        List<String> debts = (List<String>) computePairwise.invoke(bv, tiny);
        assertNotNull(debts);
        assertEquals(1, debts.size());
        assertTrue(debts.get(0).contains("Keine offenen Schulden"));
    }

    @Test
    public void testSharesWithUnknownUserAreIncluded() throws Exception {
        // Insert transaction and a share for an unknown user 'X'
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, ?, ?, ?)") ) {
                p.setString(1, "Weird"); p.setDouble(2, 50.0); p.setString(3, "Alice"); p.setString(4, "2026-01-12"); p.setString(5, "Sonstiges");
                p.executeUpdate();
            }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO budget_shares (transaction_id, username, share) VALUES (?, ?, ?)") ) {
                        ps.setInt(1, id); ps.setString(2, "Alice"); ps.setDouble(3, 0.5); ps.executeUpdate();
                        ps.setInt(1, id); ps.setString(2, "X"); ps.setDouble(3, 0.5); ps.executeUpdate();
                    }
                }
            }
        }

        BudgetView bv = new BudgetView("Alice");
        Method computeBalances = BudgetView.class.getDeclaredMethod("computeBalances");
        computeBalances.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Double> balances = (Map<String, Double>) computeBalances.invoke(bv);

        // Unknown user 'X' should appear with negative share
        assertTrue(balances.containsKey("X"));
        assertEquals(-25.0, Math.round(balances.getOrDefault("X", 0.0) * 100.0) / 100.0);
        // Alice: +50 -25 = +25
        assertEquals(25.0, Math.round(balances.getOrDefault("Alice", 0.0) * 100.0) / 100.0);
    }

    @Test
    public void testDeleteNonexistentTransactionDoesNotThrow() throws Exception {
        BudgetView bv = new BudgetView("Alice");
        Method deleteTransaction = BudgetView.class.getDeclaredMethod("deleteTransaction", int.class);
        deleteTransaction.setAccessible(true);
        // call with a non-existing id (e.g., 99999)
        deleteTransaction.invoke(bv, 99999);
        // if no exception, test passes
    }

    @Test
    public void testNoUsersAndNullPaidByProducesEmptyBalances() throws Exception {
        // Drop users table entirely to simulate no users
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try { c.createStatement().execute("DROP TABLE IF EXISTS users"); } catch (Exception ignore) {}
            // insert a transaction with null paid_by
            try (PreparedStatement p = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by, date, category) VALUES (?, ?, NULL, ?, ?)") ) {
                p.setString(1, "Anon2"); p.setDouble(2, 40.0); p.setString(3, "2026-01-13"); p.setString(4, "Sonstiges"); p.executeUpdate();
            }
        }

        BudgetView bv = new BudgetView("Alice");
        Method computeBalances = BudgetView.class.getDeclaredMethod("computeBalances");
        computeBalances.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Double> balances = (Map<String, Double>) computeBalances.invoke(bv);

        // with no users and no paid_by, balances map should be empty or contain no meaningful entries
        assertTrue(balances.isEmpty() || balances.values().stream().allMatch(v -> Math.abs(v) < 0.0001));
    }

}
