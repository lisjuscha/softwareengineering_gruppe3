package com.flatmanager.ui;

import com.flatmanager.dao.ShoppingItemDao;
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

public class IntegrationMoreTest {
    private static final String DB_FILE = "target/integration_more_test.db";

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
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS shopping_items (id INTEGER PRIMARY KEY AUTOINCREMENT, item_name TEXT, quantity INTEGER DEFAULT 1, added_by TEXT, category TEXT, purchased INTEGER DEFAULT 0, bought INTEGER DEFAULT 0, purchased_for TEXT)");
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
    public void testMonthlyRotationUsesCalendarMonths() throws Exception {
        // users
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "A"); ps.executeUpdate();
                ps.setString(1, "B"); ps.executeUpdate();
            }
        }

        // original due 2021-01-31 -> plus 1 calendar month -> 2021-02-28
        LocalDate origDue = LocalDate.of(2021, 1, 31);
        int origId;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, "MonthlyTask"); ps.setString(2, origDue.toString()); ps.setString(3, "A"); ps.setString(4, "Monatlich"); ps.setInt(5, 0); ps.setInt(6, 1); ps.executeUpdate();
            }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT last_insert_rowid()")) { rs.next(); origId = rs.getInt(1); }
        }

        CleaningScheduleView csv = new CleaningScheduleView("A");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);
        Method deleteComp = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks"); deleteComp.setAccessible(true);
        deleteComp.invoke(csv);

        // wait for async tasks
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // find new task due
        LocalDate expected = origDue.plusMonths(1);
        boolean found = false;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT due, assigned_to FROM cleaning_tasks WHERE title='MonthlyTask'")) {
                while (rs.next()) {
                    String due = rs.getString("due");
                    String assigned = rs.getString("assigned_to");
                    if (due != null && LocalDate.parse(due).equals(expected) && "B".equals(assigned)) found = true;
                }
            }
        }
        assertTrue(found, "Expected monthly rotated task with calendar month addition assigned to B and due=" + expected);
    }

    @Test
    public void testWeeklyRotationAcrossUsers() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("INSERT INTO users (username) VALUES ('U1')");
            c.createStatement().execute("INSERT INTO users (username) VALUES ('U2')");
            c.createStatement().execute("INSERT INTO users (username) VALUES ('U3')");
        }

        LocalDate due = LocalDate.now();
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "Weekly"); ps.setString(2, due.toString()); ps.setString(3, "U1"); ps.setString(4, "Wöchentlich"); ps.setInt(5, 0); ps.setInt(6, 1); ps.executeUpdate();
            }
        }

        CleaningScheduleView csv = new CleaningScheduleView("U1");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);
        Method deleteComp = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks"); deleteComp.setAccessible(true);
        deleteComp.invoke(csv);
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // ensure there is a new task and assigned rotated to U2
        boolean rotated = false;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT assigned_to, due FROM cleaning_tasks WHERE title='Weekly'")) {
                while (rs.next()) {
                    String a = rs.getString("assigned_to");
                    if ("U2".equals(a)) rotated = true;
                }
            }
        }
        assertTrue(rotated, "Expected rotation to U2");
    }

    @Test
    public void testShoppingDeleteBoughtAffectsDashboardCounts() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO shopping_items (item_name, purchased, purchased_for) VALUES (?, ?, ?)") ) {
                ps.setString(1, "X"); ps.setInt(2, 1); ps.setString(3, "alice"); ps.executeUpdate();
                ps.setString(1, "Y"); ps.setInt(2, 0); ps.setString(3, "bob"); ps.executeUpdate();
            }
        }

        ShoppingItemDao dao = new ShoppingItemDao();
        dao.init();
        // ensure 'bought' column exists (some migrations may not add it before deleteBought is used)
        try (Connection c2 = DriverManager.getConnection(System.getProperty("db.url"))) {
            try { c2.createStatement().execute("ALTER TABLE shopping_items ADD COLUMN bought INTEGER DEFAULT 0"); } catch (Exception ignore) {}
        }
        // delete bought items
        dao.deleteBought();

        DashboardScreen ds = new DashboardScreen("bob");
        Method refreshShopping = DashboardScreen.class.getDeclaredMethod("refreshShopping"); refreshShopping.setAccessible(true); refreshShopping.invoke(ds);

        Method findLabel = DashboardScreenTest.class.getDeclaredMethod("findStatLabel", DashboardScreen.class, String.class, int.class);
        findLabel.setAccessible(true);
        Object helper = DashboardScreenTest.class.getDeclaredConstructor().newInstance();
        Label total = (Label) findLabel.invoke(helper, ds, "Einkaufsliste", 0);
        Label mine = (Label) findLabel.invoke(helper, ds, "Einkaufsliste", 1);
        assertNotNull(total);
        assertNotNull(mine);
        // after deleting bought, only one item remains (Y)
        assertEquals("1", total.getText());
        // bob has 1
        assertEquals("1", mine.getText());
    }

    @Test
    public void testBudgetEqualSplitWhenNoShares() throws Exception {
        // three users, Alice pays 90, no shares -> each should have 30 debt
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("INSERT INTO users (username) VALUES ('Alice')");
            c.createStatement().execute("INSERT INTO users (username) VALUES ('Bob')");
            c.createStatement().execute("INSERT INTO users (username) VALUES ('Carol')");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO budget_transactions (description, amount, paid_by) VALUES (?, ?, ?)") ) {
                ps.setString(1, "Trip"); ps.setDouble(2, 90.0); ps.setString(3, "Alice"); ps.executeUpdate();
            }
        }

        DashboardScreen ds = new DashboardScreen("Alice");
        Method refreshFinance = DashboardScreen.class.getDeclaredMethod("refreshFinance"); refreshFinance.setAccessible(true); refreshFinance.invoke(ds);

        Method findLabel = DashboardScreenTest.class.getDeclaredMethod("findStatLabel", DashboardScreen.class, String.class, int.class);
        findLabel.setAccessible(true);
        Object helper = DashboardScreenTest.class.getDeclaredConstructor().newInstance();
        Label owed = (Label) findLabel.invoke(helper, ds, "Finanzen", 0);
        assertNotNull(owed);
        String owedText = owed.getText().replaceAll("[^0-9,.-]", "").replace(',', '.');
        double owedVal = 0.0; try { owedVal = Double.parseDouble(owedText); } catch (Exception ex) {}
        // Alice paid 90 but her share is 30 -> others owe her 60
        assertEquals(60.0, owedVal, 0.02);
    }
}
