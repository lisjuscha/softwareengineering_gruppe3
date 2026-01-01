package com.flatmanager.ui;

import com.flatmanager.dao.CleaningTaskDao;
import com.flatmanager.model.CleaningTask;
import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class CleaningScheduleViewTest {

    private static final String DB_FILE = "target/cleaning_schedule_test.db";

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

        // create minimal schema: users + cleaning_tasks
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS users (username TEXT UNIQUE)");
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS cleaning_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due TEXT, assigned_to TEXT, recurrence TEXT, urgent INTEGER DEFAULT 0, completed INTEGER DEFAULT 0)");
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        Database.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignore) {}
    }

    @Test
    public void testLoadUsersEmptyReturnsEmpty() throws Exception {
        // ensure users table is empty
        CleaningScheduleView csv = new CleaningScheduleView("alice");
        Method load = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb");
        load.setAccessible(true);
        load.invoke(csv);

        Field usersField = CleaningScheduleView.class.getDeclaredField("users");
        usersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<String> users = (javafx.collections.ObservableList<String>) usersField.get(csv);

        assertNotNull(users);
        assertEquals(0, users.size(), "Expected no users when table is empty");
    }

    @Test
    public void testLoadDataFromDbSplitsAssignedAndOpen() throws Exception {
        // insert assigned and open tasks
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "AssignedTask"); ps.setString(2, LocalDate.now().toString()); ps.setString(3, "Alice"); ps.setString(4, null); ps.setInt(5, 0); ps.setInt(6, 0); ps.executeUpdate();
                ps.setString(1, "OpenTask"); ps.setString(2, LocalDate.now().toString()); ps.setString(3, null); ps.setString(4, null); ps.setInt(5, 0); ps.setInt(6, 0); ps.executeUpdate();
            }
        }

        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method loadData = CleaningScheduleView.class.getDeclaredMethod("loadDataFromDb");
        loadData.setAccessible(true);
        loadData.invoke(csv);

        Field assignedField = CleaningScheduleView.class.getDeclaredField("assignedTasks");
        Field openField = CleaningScheduleView.class.getDeclaredField("openTasks");
        assignedField.setAccessible(true);
        openField.setAccessible(true);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<CleaningTask> assigned = (javafx.collections.ObservableList<CleaningTask>) assignedField.get(csv);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<CleaningTask> open = (javafx.collections.ObservableList<CleaningTask>) openField.get(csv);

        assertEquals(1, assigned.size());
        assertEquals("AssignedTask", assigned.get(0).getTitle());
        assertEquals(1, open.size());
        assertEquals("OpenTask", open.get(0).getTitle());
    }

    @Test
    public void testDeleteCompletedTasksCreatesNextRecurringAndDeletesCompleted() throws Exception {
        // prepare users for rotation: Alice, Bob
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "Alice"); ps.executeUpdate();
                ps.setString(1, "Bob"); ps.executeUpdate();
            }
        }

        // insert a completed, weekly task assigned to Alice
        int origId;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "CleanBathroom"); ps.setString(2, LocalDate.now().toString()); ps.setString(3, "Alice"); ps.setString(4, "Wöchentlich"); ps.setInt(5, 0); ps.setInt(6, 1); ps.executeUpdate();
            }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT last_insert_rowid()")) { rs.next(); origId = rs.getInt(1); }
        }

        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        // ensure users loaded into instance
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb");
        loadUsers.setAccessible(true);
        loadUsers.invoke(csv);

        // call deleteCompletedTasks
        Method deleteComp = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks");
        deleteComp.setAccessible(true);
        deleteComp.invoke(csv);

        // allow Platform.runLater tasks to complete
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // original should be deleted from DB
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM cleaning_tasks WHERE id = " + origId)) { rs.next(); assertEquals(0, rs.getInt(1)); }
            // new task should exist assigned to Bob
            try (ResultSet rs2 = c.createStatement().executeQuery("SELECT assigned_to, recurrence FROM cleaning_tasks WHERE title='CleanBathroom'")) {
                boolean found = false;
                while (rs2.next()) {
                    String assigned = rs2.getString("assigned_to");
                    String rec = rs2.getString("recurrence");
                    if ("Bob".equals(assigned) && "Wöchentlich".equals(rec)) found = true;
                }
                assertTrue(found, "Expected rotated recurring task assigned to Bob");
            }
        }

        // also ensure in-memory lists were updated (no completed tasks)
        Field assignedField = CleaningScheduleView.class.getDeclaredField("assignedTasks");
        Field openField = CleaningScheduleView.class.getDeclaredField("openTasks");
        assignedField.setAccessible(true); openField.setAccessible(true);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<CleaningTask> assigned = (javafx.collections.ObservableList<CleaningTask>) assignedField.get(csv);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<CleaningTask> open = (javafx.collections.ObservableList<CleaningTask>) openField.get(csv);

        assertTrue(assigned.stream().noneMatch(CleaningTask::isCompleted));
        assertTrue(open.stream().noneMatch(CleaningTask::isCompleted));
    }

    @Test
    public void testDeleteCompletedTasksDoesNotRotateWhenNoUsers() throws Exception {
        // ensure users table empty
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try { c.createStatement().execute("DELETE FROM users"); } catch (Exception ignore) {}
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "WeeklyTask"); ps.setString(2, LocalDate.now().toString()); ps.setString(3, "Alice"); ps.setString(4, "Wöchentlich"); ps.setInt(5, 0); ps.setInt(6, 1); ps.executeUpdate();
            }
        }

        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method deleteComp = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks");
        deleteComp.setAccessible(true);
        deleteComp.invoke(csv);

        // allow Platform.runLater tasks to complete
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // DB should no longer contain the completed task
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM cleaning_tasks WHERE title='WeeklyTask'")) { rs.next(); assertEquals(0, rs.getInt(1)); }
        }
    }

    @Test
    public void testCreateTaskNodeIncludesAssigneeChoices() throws Exception {
        // prepare users
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) { ps.setString(1, "Alice"); ps.executeUpdate(); ps.setString(1, "Bob"); ps.executeUpdate(); }
        }

        CleaningTask t = new CleaningTask("Vacuum", LocalDate.now().plusDays(1), null, null, false);
        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);

        Method createNode = CleaningScheduleView.class.getDeclaredMethod("createTaskNode", com.flatmanager.model.CleaningTask.class);
        createNode.setAccessible(true);
        Node node = (Node) createNode.invoke(csv, t);

        // Node is an HBox with children: CheckBox, textBox, ComboBox
        assertTrue(node instanceof HBox);
        HBox h = (HBox) node;
        boolean hasCombo = h.getChildren().stream().anyMatch(n -> n instanceof ComboBox);
        assertTrue(hasCombo, "Task node should contain an assignee ComboBox");
    }

    @Test
    public void testCreateTaskNodeCompletedAppearance() throws Exception {
        CleaningTask t = new CleaningTask("Done", LocalDate.now().minusDays(1), "Alice", null, false);
        t.setCompleted(true);
        CleaningScheduleView csv = new CleaningScheduleView("Alice");

        Method createNode = CleaningScheduleView.class.getDeclaredMethod("createTaskNode", com.flatmanager.model.CleaningTask.class);
        createNode.setAccessible(true);
        Node node = (Node) createNode.invoke(csv, t);

        assertTrue(node instanceof HBox);
        HBox h = (HBox) node;
        // opacity should be less than 1 for completed
        assertTrue(h.getOpacity() < 1.0);
    }

    @Test
    public void testCreateTaskNodeComboBoxHasDefaultNotAssigned() throws Exception {
        // prepare a user but create a task that is not assigned
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) { ps.setString(1, "Alice"); ps.executeUpdate(); }
        }

        CleaningTask t = new CleaningTask("Vacuum", LocalDate.now().plusDays(1), null, null, false);
        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);

        Method createNode = CleaningScheduleView.class.getDeclaredMethod("createTaskNode", com.flatmanager.model.CleaningTask.class);
        createNode.setAccessible(true);
        Node node = (Node) createNode.invoke(csv, t);

        assertTrue(node instanceof HBox);
        HBox h = (HBox) node;
        @SuppressWarnings("unchecked")
        boolean comboHasDefaultNotAssigned = h.getChildren().stream().filter(n -> n instanceof ComboBox).map(n -> (ComboBox<String>) n).anyMatch(cb -> "Nicht zugewiesen".equals(cb.getValue()));
        assertTrue(comboHasDefaultNotAssigned, "ComboBox should default to 'Nicht zugewiesen'");
    }

    @Test
    public void testGetDueTextTodayFuturePast() throws Exception {
        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method getDueText = CleaningScheduleView.class.getDeclaredMethod("getDueText", com.flatmanager.model.CleaningTask.class);
        getDueText.setAccessible(true);

        CleaningTask today = new CleaningTask("T", LocalDate.now(), null, null, false);
        String sToday = (String) getDueText.invoke(csv, today);
        assertTrue(sToday.equals("heute") || sToday.contains("Tage")); // tolerant to locale formatting

        CleaningTask future = new CleaningTask("F", LocalDate.now().plusDays(5), null, null, false);
        String sFuture = (String) getDueText.invoke(csv, future);
        assertTrue(sFuture.contains("in") || sFuture.contains("Tage"));

        CleaningTask past = new CleaningTask("P", LocalDate.now().minusDays(3), null, null, false);
        String sPast = (String) getDueText.invoke(csv, past);
        assertTrue(sPast.contains("Tage") || sPast.matches("-?\\d+ Tage"));
    }

    @Test
    public void testDeleteCompletedTasksRotatesMonthly() throws Exception {
        // prepare users for rotation: A,B
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "A"); ps.executeUpdate();
                ps.setString(1, "B"); ps.executeUpdate();
            }
        }

        // insert a completed, monthly task assigned to A
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "MonthlyTask"); ps.setString(2, LocalDate.now().toString()); ps.setString(3, "A"); ps.setString(4, "Monatlich"); ps.setInt(5, 0); ps.setInt(6, 1); ps.executeUpdate();
            }
        }

        CleaningScheduleView csv = new CleaningScheduleView("A");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);

        Method deleteComp = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks"); deleteComp.setAccessible(true);
        deleteComp.invoke(csv);

        // allow Platform.runLater tasks
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // new task should exist assigned to B
        boolean found = false;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT assigned_to, recurrence FROM cleaning_tasks WHERE title='MonthlyTask'")) {
                while (rs.next()) {
                    String assigned = rs.getString("assigned_to");
                    String rec = rs.getString("recurrence");
                    if ("B".equals(assigned) && "Monatlich".equals(rec)) found = true;
                }
            }
        }
        assertTrue(found, "Expected rotated monthly task assigned to B");
    }

    @Test
    public void testCreateTaskNodeWhenNoUsersOnlyNotAssigned() throws Exception {
        // ensure users table is empty
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) { try { c.createStatement().execute("DELETE FROM users"); } catch (Exception ignore) {} }

        CleaningTask t = new CleaningTask("Solo", LocalDate.now().plusDays(2), null, null, false);
        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);

        Method createNode = CleaningScheduleView.class.getDeclaredMethod("createTaskNode", com.flatmanager.model.CleaningTask.class);
        createNode.setAccessible(true);
        Node node = (Node) createNode.invoke(csv, t);
        assertTrue(node instanceof HBox);
        HBox h = (HBox) node;
        @SuppressWarnings("unchecked")
        java.util.Optional<ComboBox<String>> cbOpt = h.getChildren().stream().filter(n -> n instanceof ComboBox).map(n -> (ComboBox<String>) n).findFirst();
        assertTrue(cbOpt.isPresent());
        ComboBox<String> cb = cbOpt.get();
        // only one value 'Nicht zugewiesen'
        assertEquals(1, cb.getItems().size());
        assertEquals("Nicht zugewiesen", cb.getItems().get(0));
    }

    @Test
    public void testCreateTaskNodeShowsAssignedUserByDefault() throws Exception {
        // prepare users
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) { ps.setString(1, "U1"); ps.executeUpdate(); ps.setString(1, "U2"); ps.executeUpdate(); }
        }
        CleaningTask t = new CleaningTask("Assigned", LocalDate.now().plusDays(2), "U2", null, false);
        CleaningScheduleView csv = new CleaningScheduleView("U1");
        Method loadUsers = CleaningScheduleView.class.getDeclaredMethod("loadUsersFromDb"); loadUsers.setAccessible(true); loadUsers.invoke(csv);

        Method createNode = CleaningScheduleView.class.getDeclaredMethod("createTaskNode", com.flatmanager.model.CleaningTask.class);
        createNode.setAccessible(true);
        HBox h = (HBox) createNode.invoke(csv, t);
        @SuppressWarnings("unchecked")
        java.util.Optional<ComboBox<String>> cbOpt = h.getChildren().stream().filter(n -> n instanceof ComboBox).map(n -> (ComboBox<String>) n).findFirst();
        assertTrue(cbOpt.isPresent());
        ComboBox<String> cb = cbOpt.get();
        assertEquals("U2", cb.getValue());
    }

    @Test
    public void testTasksBeyond30DaysAreHidden() throws Exception {
        // insert a task due after the coming month (should be hidden)
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "FarFuture"); ps.setString(2, LocalDate.now().plusMonths(1).plusDays(1).toString()); ps.setString(3, null); ps.setString(4, null); ps.setInt(5, 0); ps.setInt(6, 0); ps.executeUpdate();
            }
        }

        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method loadData = CleaningScheduleView.class.getDeclaredMethod("loadDataFromDb");
        loadData.setAccessible(true);
        loadData.invoke(csv);

        Field assignedField = CleaningScheduleView.class.getDeclaredField("assignedTasks");
        Field openField = CleaningScheduleView.class.getDeclaredField("openTasks");
        assignedField.setAccessible(true);
        openField.setAccessible(true);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<CleaningTask> assigned = (javafx.collections.ObservableList<CleaningTask>) assignedField.get(csv);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<CleaningTask> open = (javafx.collections.ObservableList<CleaningTask>) openField.get(csv);

        // No tasks should be visible since the only task is beyond the coming month
        assertEquals(0, assigned.size());
        assertEquals(0, open.size());
    }

    @Test
    public void testTasksAt30DaysAreVisible() throws Exception {
        // insert a task due in exactly one month (should be visible)
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "ThirtyDays"); ps.setString(2, LocalDate.now().plusMonths(1).toString()); ps.setString(3, null); ps.setString(4, null); ps.setInt(5, 0); ps.setInt(6, 0); ps.executeUpdate();
            }
        }

        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method loadData = CleaningScheduleView.class.getDeclaredMethod("loadDataFromDb");
        loadData.setAccessible(true);
        loadData.invoke(csv);

        Field openField = CleaningScheduleView.class.getDeclaredField("openTasks");
        openField.setAccessible(true);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<CleaningTask> open = (javafx.collections.ObservableList<CleaningTask>) openField.get(csv);

        assertEquals(1, open.size());
        assertEquals("ThirtyDays", open.get(0).getTitle());
    }

    @Test
    public void testTasksWithoutDueAlwaysVisible() throws Exception {
        // insert a task without due date (should always be visible)
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cleaning_tasks (title, due, assigned_to, recurrence, urgent, completed) VALUES (?, ?, ?, ?, ?, ?)") ) {
                ps.setString(1, "NoDue"); ps.setString(2, null); ps.setString(3, null); ps.setString(4, null); ps.setInt(5, 0); ps.setInt(6, 0); ps.executeUpdate();
            }
        }

        CleaningScheduleView csv = new CleaningScheduleView("Alice");
        Method loadData = CleaningScheduleView.class.getDeclaredMethod("loadDataFromDb");
        loadData.setAccessible(true);
        loadData.invoke(csv);

        Field openField = CleaningScheduleView.class.getDeclaredField("openTasks");
        openField.setAccessible(true);
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<CleaningTask> open = (javafx.collections.ObservableList<CleaningTask>) openField.get(csv);

        assertEquals(1, open.size());
        assertEquals("NoDue", open.get(0).getTitle());
    }
}
