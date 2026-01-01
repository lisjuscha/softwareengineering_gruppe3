package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class DashboardScreenTest {
    private static final String DB_FILE = "target/dashboard_test.db";

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

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS users (username TEXT UNIQUE, name TEXT)");
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS cleaning_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, due TEXT, assigned_to TEXT, recurrence TEXT, urgent INTEGER DEFAULT 0, completed INTEGER DEFAULT 0)");
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS shopping_items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, purchased_for TEXT)");
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS budget_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, description TEXT, amount REAL, paid_by TEXT)");
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS budget_shares (id INTEGER PRIMARY KEY AUTOINCREMENT, transaction_id INTEGER, username TEXT, share REAL)");
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        Database.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignored) {}
    }

    private Label findStatLabel(DashboardScreen ds, String cardHeader, int index) throws Exception {
        Field contentField = DashboardScreen.class.getDeclaredField("contentArea");
        contentField.setAccessible(true);
        @SuppressWarnings("unchecked")
        javafx.scene.layout.VBox content = (VBox) contentField.get(ds);

        Method getCardHeaderText = DashboardScreen.class.getDeclaredMethod("getCardHeaderText", TitledPane.class);
        getCardHeaderText.setAccessible(true);

        for (Node n : content.getChildren()) {
            if (n instanceof TitledPane) {
                TitledPane tp = (TitledPane) n;
                String header = (String) getCardHeaderText.invoke(ds, tp);
                if (cardHeader.equals(header)) {
                    VBox[] boxes = (VBox[]) tp.getUserData();
                    Node v = boxes[index].lookup("#stat-value");
                    if (v instanceof Label) return (Label) v;
                }
            }
        }
        return null;
    }

    @Test
    public void testRefreshTasksUpdatesValues() throws Exception {
        // insert tasks: one assigned to 'john', one open
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, "T1"); ps.setString(2, null); ps.setString(3, "john"); ps.setString(4, null); ps.setInt(5,0); ps.setInt(6,0); ps.executeUpdate();
                ps.setString(1, "T2"); ps.setString(2, null); ps.setString(3, null); ps.setString(4, null); ps.setInt(5,0); ps.setInt(6,0); ps.executeUpdate();
            }
        }

        DashboardScreen ds = new DashboardScreen("john");
        // ensure refreshTasks invoked
        Method refreshTasks = DashboardScreen.class.getDeclaredMethod("refreshTasks");
        refreshTasks.setAccessible(true);
        refreshTasks.invoke(ds);

        Label myLabel = findStatLabel(ds, "Aufgaben", 0);
        Label openLabel = findStatLabel(ds, "Aufgaben", 1);
        assertNotNull(myLabel);
        assertNotNull(openLabel);
        assertEquals("1", myLabel.getText());
        assertEquals("2", openLabel.getText()); // openCount counts non-completed (2)
    }

    @Test
    public void testRefreshShoppingUpdatesValues() throws Exception {
        // insert shopping items
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO shopping_items (name, purchased_for) VALUES (?, ?)")) {
                ps.setString(1, "Milk"); ps.setString(2, "john"); ps.executeUpdate();
                ps.setString(1, "Eggs"); ps.setString(2, null); ps.executeUpdate();
            }
        }

        DashboardScreen ds = new DashboardScreen("john");
        Method refreshShopping = DashboardScreen.class.getDeclaredMethod("refreshShopping");
        refreshShopping.setAccessible(true);
        refreshShopping.invoke(ds);

        Label total = findStatLabel(ds, "Einkaufsliste", 0);
        Label mine = findStatLabel(ds, "Einkaufsliste", 1);
        assertNotNull(total);
        assertNotNull(mine);
        assertEquals("2", total.getText());
        assertEquals("1", mine.getText());
    }

    @Test
    public void testRefreshFinanceComputesBalances() throws Exception {
        // users
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("INSERT INTO users (username) VALUES ('Alice')");
            c.createStatement().execute("INSERT INTO users (username) VALUES ('Bob')");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by) VALUES (?, ?, ?)") ) {
                ps.setString(1, "Shop"); ps.setDouble(2, 100.0); ps.setString(3, "Alice"); ps.executeUpdate();
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

        DashboardScreen ds = new DashboardScreen("Alice");
        Method refreshFinance = DashboardScreen.class.getDeclaredMethod("refreshFinance");
        refreshFinance.setAccessible(true);
        refreshFinance.invoke(ds);

        Label owed = findStatLabel(ds, "Finanzen", 0);
        Label owe = findStatLabel(ds, "Finanzen", 1);
        assertNotNull(owed);
        assertNotNull(owe);
        // parse numeric values from the labels in a locale-robust way
        String owedText = owed.getText().replaceAll("[^0-9,.-]", "").replace(',', '.');
        String oweText = owe.getText().replaceAll("[^0-9,.-]", "").replace(',', '.');
        double owedVal = 0.0; double oweVal = 0.0;
        try { owedVal = Double.parseDouble(owedText); } catch (Exception ex) { /* fallback keep 0 */ }
        try { oweVal = Double.parseDouble(oweText); } catch (Exception ex) { /* fallback keep 0 */ }
        assertEquals(50.0, owedVal, 0.02);
        assertEquals(0.0, oweVal, 0.02);
    }

    @Test
    public void testNotifyRefreshNowUsesActiveInstance() throws Exception {
        DashboardScreen ds = new DashboardScreen("john");
        // register as active via private static method
        Method register = DashboardScreen.class.getDeclaredMethod("registerActive", DashboardScreen.class);
        register.setAccessible(true);
        register.invoke(null, ds);

        // insert a shopping item after creation
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO shopping_items (name, purchased_for) VALUES (?, ?)") ) {
                ps.setString(1, "Bread"); ps.setString(2, "john"); ps.executeUpdate();
            }
        }

        // call notifyRefreshNow which schedules a Platform.runLater to call refreshAll
        DashboardScreen.notifyRefreshNow();

        // wait for queued runLater tasks
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        Label total = findStatLabel(ds, "Einkaufsliste", 0);
        Label mine = findStatLabel(ds, "Einkaufsliste", 1);
        assertNotNull(total);
        assertNotNull(mine);
        assertEquals("1", mine.getText());
    }

    @Test
    public void testRefreshTasksWithNoTasksShowsZero() throws Exception {
        // ensure no tasks in DB
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) { c.createStatement().execute("DELETE FROM cleaning_tasks"); }

        DashboardScreen ds = new DashboardScreen("someone");
        Method refreshTasks = DashboardScreen.class.getDeclaredMethod("refreshTasks");
        refreshTasks.setAccessible(true);
        refreshTasks.invoke(ds);

        Label myLabel = findStatLabel(ds, "Aufgaben", 0);
        Label openLabel = findStatLabel(ds, "Aufgaben", 1);
        assertNotNull(myLabel);
        assertNotNull(openLabel);
        assertEquals("0", myLabel.getText());
        assertEquals("0", openLabel.getText());
    }

    @Test
    public void testRefreshFinanceNoTransactionsShowsZeros() throws Exception {
        // ensure no transactions/shares
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("DELETE FROM budget_transactions");
            c.createStatement().execute("DELETE FROM budget_shares");
            c.createStatement().execute("DELETE FROM users");
        }

        DashboardScreen ds = new DashboardScreen("anyuser");
        Method refreshFinance = DashboardScreen.class.getDeclaredMethod("refreshFinance");
        refreshFinance.setAccessible(true);
        refreshFinance.invoke(ds);

        Label owed = findStatLabel(ds, "Finanzen", 0);
        Label owe = findStatLabel(ds, "Finanzen", 1);
        assertNotNull(owed);
        assertNotNull(owe);
        String owedText = owed.getText().replaceAll("[^0-9,.-]", "").replace(',', '.');
        String oweText = owe.getText().replaceAll("[^0-9,.-]", "").replace(',', '.');
        double owedVal = 0.0; double oweVal = 0.0;
        try { owedVal = Double.parseDouble(owedText); } catch (Exception ex) {}
        try { oweVal = Double.parseDouble(oweText); } catch (Exception ex) {}
        assertEquals(0.0, owedVal, 0.02);
        assertEquals(0.0, oweVal, 0.02);
    }

    @Test
    public void testResolveUsernameExtractsParentheses() throws Exception {
        // insert a user with username 'u1' and name 'Full Name'
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO users (username, name) VALUES (?, ?)") ) {
                ps.setString(1, "u1"); ps.setString(2, "Full Name"); ps.executeUpdate();
            }
        }

        DashboardScreen ds = new DashboardScreen("Full Name (u1)");
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method resolve = DashboardScreen.class.getDeclaredMethod("resolveUsername", java.sql.Connection.class, String.class);
            resolve.setAccessible(true);
            String resolved = (String) resolve.invoke(ds, c, "Full Name (u1)");
            assertEquals("u1", resolved);
        }
    }

    @Test
    public void testGetCardHeaderTextWithDirectLabelGraphic() throws Exception {
        DashboardScreen ds = new DashboardScreen("any");
        // build a titled pane with a Label as graphic and verify
        javafx.scene.control.TitledPane tp = new javafx.scene.control.TitledPane();
        javafx.scene.control.Label g = new javafx.scene.control.Label("SOMETHING");
        tp.setGraphic(g);

        Method getCardHeaderText = DashboardScreen.class.getDeclaredMethod("getCardHeaderText", javafx.scene.control.TitledPane.class);
        getCardHeaderText.setAccessible(true);
        String header = (String) getCardHeaderText.invoke(ds, tp);
        assertEquals("SOMETHING", header);
    }

}
