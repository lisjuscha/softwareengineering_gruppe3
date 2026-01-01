package com.flatmanager.ui;

import com.flatmanager.dao.CleaningTaskDao;
import com.flatmanager.model.CleaningTask;
import com.flatmanager.storage.Database;
import com.flatmanager.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CleaningRotationEdgecaseTest {
    private static final String DB_FILE = "target/cleaning_rotation_edgecases.db";

    @BeforeAll
    public static void initToolkit() {
        try { javafx.application.Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
    }

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

    // small helper like in existing tests
    private static boolean waitFor(Supplier<Boolean> cond, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (cond.get()) return true;
            Thread.sleep(50);
        }
        return false;
    }

    @Test
    public void testMonthlyRotationAcrossLeapYear_Jan31_to_Feb29() throws Exception {
        // 2020 is a leap year: Jan 31 + 1 month -> Feb 29
        DatabaseManager.createOrUpdateUser("alice", "pw", "Alice");
        DatabaseManager.createOrUpdateUser("bob", "pw", "Bob");

        CleaningTaskDao dao = new CleaningTaskDao();
        dao.init();

        LocalDate due = LocalDate.of(2020, 1, 31);
        CleaningTask task = new CleaningTask("M" , due, "alice", "Monatlich", false);
        dao.insert(task);

        CleaningScheduleView view = new CleaningScheduleView("alice");

        task.setCompleted(true);
        dao.update(task);

        Method del = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks");
        del.setAccessible(true);
        del.invoke(view);

        LocalDate expected = due.plusMonths(1); // 2020-02-29
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

        assertTrue(ok, "Expected rotated monthly task for bob on leap-year Feb 29");
    }

    @Test
    public void testMonthlyRotationFromFeb29_to_Mar29() throws Exception {
        // Feb 29, 2020 + 1 month -> Mar 29, 2020
        DatabaseManager.createOrUpdateUser("alice", "pw", "Alice");
        DatabaseManager.createOrUpdateUser("bob", "pw", "Bob");

        CleaningTaskDao dao = new CleaningTaskDao();
        dao.init();

        LocalDate due = LocalDate.of(2020, 2, 29);
        CleaningTask task = new CleaningTask("LeapTask" , due, "alice", "Monatlich", false);
        dao.insert(task);

        CleaningScheduleView view = new CleaningScheduleView("alice");

        task.setCompleted(true);
        dao.update(task);

        Method del = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks");
        del.setAccessible(true);
        del.invoke(view);

        LocalDate expected = due.plusMonths(1); // 2020-03-29
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

        assertTrue(ok, "Expected rotated monthly task for bob on Mar 29 from Feb 29 start");
    }

    @Test
    public void testSingleUserRotationKeepsSameAssignee() throws Exception {
        // only one user -> rotation should assign again to the same user
        DatabaseManager.createOrUpdateUser("solo", "pw", "Solo");

        CleaningTaskDao dao = new CleaningTaskDao();
        dao.init();

        LocalDate due = LocalDate.of(2022, 3, 1);
        CleaningTask task = new CleaningTask("Sweep", due, "solo", "Wöchentlich", false);
        dao.insert(task);

        CleaningScheduleView view = new CleaningScheduleView("solo");

        task.setCompleted(true);
        dao.update(task);

        Method del = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks");
        del.setAccessible(true);
        del.invoke(view);

        LocalDate expected = due.plusDays(7);
        boolean ok = waitFor(() -> {
            try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM cleaning_tasks WHERE assigned_to = ? AND due = ?")) {
                    ps.setString(1, "solo");
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

        assertTrue(ok, "Expected rotated weekly task for solo assigned again to same user");
    }

    @Test
    public void testMultiUserWrapAroundRotation() throws Exception {
        // users u1,u2,u3 - task assigned to u3 -> next should be u1
        DatabaseManager.createOrUpdateUser("u1", "pw", "U1");
        DatabaseManager.createOrUpdateUser("u2", "pw", "U2");
        DatabaseManager.createOrUpdateUser("u3", "pw", "U3");

        CleaningTaskDao dao = new CleaningTaskDao();
        dao.init();

        LocalDate due = LocalDate.of(2022, 6, 10);
        CleaningTask task = new CleaningTask("Wrap", due, "u3", "Wöchentlich", false);
        dao.insert(task);

        CleaningScheduleView view = new CleaningScheduleView("u1");

        task.setCompleted(true);
        dao.update(task);

        Method del = CleaningScheduleView.class.getDeclaredMethod("deleteCompletedTasks");
        del.setAccessible(true);
        del.invoke(view);

        LocalDate expected = due.plusDays(7);
        boolean ok = waitFor(() -> {
            try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM cleaning_tasks WHERE assigned_to = ? AND due = ?")) {
                    ps.setString(1, "u1");
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

        assertTrue(ok, "Expected rotated weekly task to wrap around to u1");
    }
}
