package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.control.Label;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {
    private static final String DB_FILE = "target/integration_test.db";

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
    public void teardown() throws Exception {
        Database.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignored) {}
    }

    @Test
    public void testCleaningRotationAndDashboardIntegration() throws Exception {
        // prepare users
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "Alice"); ps.executeUpdate();
                ps.setString(1, "Bob"); ps.executeUpdate();
            }
        }

        // insert a completed weekly task assigned to Alice
        int origId;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "Vacuum"); ps.setString(2, LocalDate.now().toString()); ps.setString(3, "Alice"); ps.setString(4, "Wöchentlich"); ps.setInt(5, 0); ps.setInt(6, 1); ps.executeUpdate();
            }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT last_insert_rowid()")) { rs.next(); origId = rs.getInt(1); }
        }

        // call deleteCompletedTasks on CleaningScheduleView to trigger rotation
        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);
        Method deleteComp = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks"); deleteComp.setAccessible(true);
        deleteComp.invoke(csv);

        // wait for UI tasks
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // original deleted and new task for Bob exists
        boolean foundBob = false;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM cleaning_tasks WHERE id = " + origId)) { rs.next(); assertEquals(0, rs.getInt(1)); }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT assigned_to FROM cleaning_tasks WHERE title='Vacuum'")) {
                while (rs.next()) {
                    String assigned = rs.getString("assigned_to");
                    if ("Bob".equals(assigned)) foundBob = true;
                }
            }
        }
        assertTrue(foundBob, "Expected a rotated task assigned to Bob");

        // Dashboard should reflect counts: instantiate dashboard and refresh tasks
        DashboardScreen ds = new DashboardScreen("Bob");
        Method refreshTasks = DashboardScreen.class.getDeclaredMethod("refreshTasks"); refreshTasks.setAccessible(true); refreshTasks.invoke(ds);
        // find labels
        Method findLabel = DashboardScreenTest.class.getDeclaredMethod("findStatLabel", DashboardScreen.class, String.class, int.class);
        findLabel.setAccessible(true);
        Object helper = DashboardScreenTest.class.getDeclaredConstructor().newInstance();
        Label myLabel = (Label) findLabel.invoke(helper, ds, "Aufgaben", 0);
        assertNotNull(myLabel);
        // Bob should have at least 1 assigned
        assertTrue(Integer.parseInt(myLabel.getText()) >= 1);
    }

    @Test
    public void testBudgetTransactionIntegration() throws Exception {
        // create users
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("INSERT INTO users (username) VALUES ('Alice')");
            c.createStatement().execute("INSERT INTO users (username) VALUES ('Bob')");
        }

        // insert a transaction Alice paid 120, shares 50/50
        int txId;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by) VALUES (?, ?, ?)") ) {
                ps.setString(1, "Dinner"); ps.setDouble(2, 120.0); ps.setString(3, "Alice"); ps.executeUpdate();
            }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT last_insert_rowid()")) { rs.next(); txId = rs.getInt(1); }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO budget_shares (transaction_id, username, share) VALUES (?, ?, ?)") ) {
                ps.setInt(1, txId); ps.setString(2, "Alice"); ps.setDouble(3, 0.5); ps.executeUpdate();
                ps.setInt(1, txId); ps.setString(2, "Bob"); ps.setDouble(3, 0.5); ps.executeUpdate();
            }
        }

        DashboardScreen ds = new DashboardScreen("Alice");
        Method refreshFinance = DashboardScreen.class.getDeclaredMethod("refreshFinance"); refreshFinance.setAccessible(true); refreshFinance.invoke(ds);
        // parse owed label
        Method findLabel = DashboardScreenTest.class.getDeclaredMethod("findStatLabel", DashboardScreen.class, String.class, int.class);
        findLabel.setAccessible(true);
        Object helper = DashboardScreenTest.class.getDeclaredConstructor().newInstance();
        Label owed = (Label) findLabel.invoke(helper, ds, "Finanzen", 0);
        assertNotNull(owed);
        String owedText = owed.getText().replaceAll("[^0-9,.-]", "").replace(',', '.');
        double owedVal = 0.0; try { owedVal = Double.parseDouble(owedText); } catch (Exception ex) {}
        assertEquals(60.0, owedVal, 0.02);
    }

    @Test
    public void testShoppingIntegrationAndDashboard() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO shopping_items (name, purchased_for) VALUES (?, ?)") ) {
                ps.setString(1, "Bread"); ps.setString(2, "anna"); ps.executeUpdate();
                ps.setString(1, "Butter"); ps.setString(2, "bob"); ps.executeUpdate();
            }
        }

        DashboardScreen ds = new DashboardScreen("bob");
        Method refreshShopping = DashboardScreen.class.getDeclaredMethod("refreshShopping"); refreshShopping.setAccessible(true); refreshShopping.invoke(ds);
        Method findLabel = DashboardScreenTest.class.getDeclaredMethod("findStatLabel", DashboardScreen.class, String.class, int.class);
        findLabel.setAccessible(true);
        Object helper = DashboardScreenTest.class.getDeclaredConstructor().newInstance();
        Label total = (Label) findLabel.invoke(helper, ds, "Einkaufsliste", 0);
        Label mine = (Label) findLabel.invoke(helper, ds, "Einkaufsliste", 1);
        assertNotNull(total);
        assertNotNull(mine);
        assertEquals("2", total.getText());
        assertEquals("1", mine.getText());
    }

    @Test
    public void testUserLifecycleLoad() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, name) VALUES (?, ?)") ) {
                ps.setString(1, "charlie"); ps.setString(2, "Charlie Brown"); ps.executeUpdate();
            }
        }

        CleaningScheduleView csv = new CleaningScheduleView("charlie");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);
        // access private users list field
        java.lang.reflect.Field usersField = CleaningScheduleView.class.getDeclaredField("users");
        usersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<String> users = (javafx.collections.ObservableList<String>) usersField.get(csv);
        assertTrue(users.stream().anyMatch(u -> "charlie".equalsIgnoreCase(u)));
    }
}
