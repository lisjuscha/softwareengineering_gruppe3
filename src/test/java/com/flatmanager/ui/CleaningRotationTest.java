package com.flatmanager.ui;

import com.flatmanager.dao.CleaningTaskDao;
import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.CleaningTask;
import com.flatmanager.storage.Database;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CleaningRotationTest {
    private static final String DB_FILE = "target/cleaning_rotation_test.db";

    @BeforeAll
    public static void initToolkit() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
    }

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) Files.delete(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        // initialize schema
        Database.init();
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    // Helper to wait for a condition with timeout
    private static boolean waitFor(Supplier<Boolean> cond, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (cond.get()) return true;
            Thread.sleep(50);
        }
        return false;
    }

    @Test
    public void testWeeklyRotationRotatesUsers() throws Exception {
        // create two users alice and bob
        DatabaseManager.createOrUpdateUser("alice", "pw", "Alice");
        DatabaseManager.createOrUpdateUser("bob", "pw", "Bob");

        CleaningTaskDao dao = new CleaningTaskDao();
        dao.init();

        LocalDate due = LocalDate.of(2021, 1, 1);
        CleaningTask task = new CleaningTask("Bad putzen", due, "alice", "Wöchentlich", false);
        dao.insert(task);
        assertTrue(task.getId() != null && task.getId() > 0);

        // create view which loads users and tasks
        CleaningScheduleView view = new CleaningScheduleView("alice");

        // mark the task completed via DAO (simulate the user action)
        task.setCompleted(true);
        dao.update(task);

        // invoke private deleteCompletedTasks() to trigger rotation synchronously
        Method del = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks");
        del.setAccessible(true);
        del.invoke(view);

        // DEBUG: dump cleaning_tasks rows (assigned_to, due, recurrence)
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = c.prepareStatement("SELECT id, title, assigned_to, due, recurrence, completed FROM cleaning_tasks ORDER BY id")) {
            try (ResultSet rs = ps.executeQuery()) {
                System.err.println("--- cleaning_tasks dump ---");
                while (rs.next()) {
                    System.err.println(String.format("id=%d title=%s assigned_to=%s due=%s rec=%s completed=%d",
                            rs.getInt("id"), rs.getString("title"), rs.getString("assigned_to"), rs.getString("due"), rs.getString("recurrence"), rs.getInt("completed")
                    ));
                }
                System.err.println("--- end dump ---");
            }
        }

        // wait until a new task appears in DB assigned to bob with due + 7 days
        LocalDate expected = due.plusDays(7);
        boolean ok = waitFor(() -> {
            try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM cleaning_tasks WHERE assigned_to = ? AND due = ?")) {
                    ps.setString(1, "bob");
                    ps.setString(2, expected.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getInt(1) > 0;
                    }
                }
            } catch (Exception e) {
                return false;
            }
            return false;
        }, 2000);

        assertTrue(ok, "Expected rotated weekly task for bob with due +7 days");
    }

    @Test
    public void testMonthlyRotationUsesCalendarMonths() throws Exception {
        // create two users alice and bob
        DatabaseManager.createOrUpdateUser("alice", "pw", "Alice");
        DatabaseManager.createOrUpdateUser("bob", "pw", "Bob");

        CleaningTaskDao dao = new CleaningTaskDao();
        dao.init();

        // Use 2021-01-31 -> plusMonths(1) should be 2021-02-28
        LocalDate due = LocalDate.of(2021, 1, 31);
        CleaningTask task = new CleaningTask("Fenster", due, "alice", "Monatlich", false);
        dao.insert(task);
        assertTrue(task.getId() != null && task.getId() > 0);

        CleaningScheduleView view = new CleaningScheduleView("alice");

        // mark completed and trigger deletion/rotation synchronously
        task.setCompleted(true);
        dao.update(task);
        Method del = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks");
        del.setAccessible(true);
        del.invoke(view);

        LocalDate expected = due.plusMonths(1); // should be 2021-02-28
        boolean ok = waitFor(() -> {
            try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM cleaning_tasks WHERE assigned_to = ? AND due = ?")) {
                    ps.setString(1, "bob");
                    ps.setString(2, expected.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getInt(1) > 0;
                    }
                }
            } catch (Exception e) {
                return false;
            }
            return false;
        }, 2000);

        assertTrue(ok, "Expected rotated monthly task for bob with due plus one calendar month");
    }
}
