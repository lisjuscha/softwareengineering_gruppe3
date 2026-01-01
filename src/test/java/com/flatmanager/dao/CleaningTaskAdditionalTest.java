package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.CleaningTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CleaningTaskAdditionalTest {

    private final CleaningTaskDao dao = new CleaningTaskDao();
    private static final String DB_FILE = "target/cleaning_additional.db";

    @BeforeEach
    void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
        dao.init();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignore) {}
    }

    @Test
    void testListCompletedAndDeleteBehavior() throws Exception {
        CleaningTask t1 = new CleaningTask("Bad", LocalDate.now(), "user1", "Wöchentlich", false);
        CleaningTask t2 = new CleaningTask("Küche", LocalDate.now(), null, null, false);
        dao.insert(t1);
        dao.insert(t2);

        // mark t1 completed
        t1.setCompleted(true);
        dao.update(t1);

        List<CleaningTask> completed = dao.listCompleted();
        assertTrue(completed.stream().anyMatch(x -> x.getId().equals(t1.getId())));

        // delete completed and ensure removed
        dao.deleteCompleted();
        List<CleaningTask> all = dao.listAll();
        assertFalse(all.stream().anyMatch(x -> x.getId().equals(t1.getId())));
        assertTrue(all.stream().anyMatch(x -> x.getId().equals(t2.getId())));
    }

    @Test
    void testLeapDayDueStoredAndLoaded() throws Exception {
        CleaningTask t = new CleaningTask("Schaltjahr", LocalDate.of(2024,2,29), null, null, false);
        dao.insert(t);
        CleaningTask loaded = dao.listAll().stream().filter(x -> x.getId().equals(t.getId())).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(LocalDate.of(2024,2,29), loaded.getDue());
    }

    @Test
    void testVeryLongTitleAndSpecialChars() throws Exception {
        String longTitle = "T".repeat(100000);
        String special = "Üñíçødé 🚀 \"quotes\" 'single'";
        String title = longTitle + special;
        CleaningTask t = new CleaningTask(title, LocalDate.of(2025,1,1), "u", "Monatlich", true);
        dao.insert(t);
        CleaningTask loaded = dao.listAll().stream().filter(x -> x.getId().equals(t.getId())).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(title.length(), loaded.getTitle().length());
        assertEquals(title, loaded.getTitle());
    }

    @Test
    void testRecurrenceValuesPersist() throws Exception {
        CleaningTask weekly = new CleaningTask("Wochentask", LocalDate.now(), "a", "Wöchentlich", false);
        CleaningTask monthly = new CleaningTask("Monattask", LocalDate.now(), "b", "Monatlich", false);
        CleaningTask none = new CleaningTask("Einmaltask", LocalDate.now(), "c", null, false);
        dao.insert(weekly);
        dao.insert(monthly);
        dao.insert(none);

        List<CleaningTask> all = dao.listAll();
        CleaningTask w = all.stream().filter(x -> x.getId().equals(weekly.getId())).findFirst().orElse(null);
        CleaningTask m = all.stream().filter(x -> x.getId().equals(monthly.getId())).findFirst().orElse(null);
        CleaningTask n = all.stream().filter(x -> x.getId().equals(none.getId())).findFirst().orElse(null);
        assertNotNull(w);
        assertNotNull(m);
        assertNotNull(n);
        assertEquals("Wöchentlich", w.getRecurrence());
        assertEquals("Monatlich", m.getRecurrence());
        assertTrue(n.getRecurrence() == null || n.getRecurrence().equals("Einmalig"));
    }

    @Test
    void testTableStillExistsAfterSpecialTitleInsert() throws Exception {
        String desc = "Robert'); DROP TABLE cleaning_tasks;--";
        CleaningTask t = new CleaningTask(desc, LocalDate.of(2021,1,1), null, null, false);
        dao.insert(t);
        CleaningTask loaded = dao.listAll().stream().filter(x -> x.getId().equals(t.getId())).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(desc, loaded.getTitle());

        try (java.sql.Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='cleaning_tasks'")) {
            assertTrue(rs.next());
        }
    }
}

