package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.CleaningTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CleaningTaskDaoTest {

    private final CleaningTaskDao dao = new CleaningTaskDao();

    @BeforeEach
    void before() throws Exception {
        // Use a file-based temporary DB for stability across connections in tests
        File dbFile = new File("target/cleaning_test.db");
        if (dbFile.exists()) dbFile.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + dbFile.getAbsolutePath());
        DatabaseManager.closeConnection();
        dao.init();
    }

    @AfterEach
    void after() {
        DatabaseManager.closeConnection();
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/cleaning_test.db"));
        } catch (Exception ignore) {
        }
    }

    @Test
    void testInitIsIdempotentAndTableExists() throws Exception {
        // zweiter Aufruf darf keine Exception werfen
        dao.init();

        // Tabelle muss vorhanden sein
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='cleaning_tasks'")) {
            assertTrue(rs.next(), "Tabelle cleaning_tasks sollte existieren");
        }
    }

    @Test
    void testInsertAndListAll() throws Exception {
        CleaningTask t1 = new CleaningTask("Bad putzen", LocalDate.of(2025, 12, 1), "alice", "Einmalig", true);
        dao.insert(t1);
        assertNotNull(t1.getId(), "insert should set id");

        List<CleaningTask> list = dao.listAll();
        assertEquals(1, list.size());
        CleaningTask loaded = list.get(0);
        assertEquals("Bad putzen", loaded.getTitle());
        assertEquals(LocalDate.of(2025, 12, 1), loaded.getDue());
        assertEquals("alice", loaded.getAssignedTo());
        assertTrue(loaded.isUrgent());
    }

    @Test
    void testUpdateAndDeleteCompleted() throws Exception {
        CleaningTask t = new CleaningTask("Küche säubern", null, null, null, false);
        dao.insert(t);
        assertNotNull(t.getId());

        t.setCompleted(true);
        dao.update(t);

        List<CleaningTask> after = dao.listAll();
        CleaningTask updated = after.stream().filter(x -> x.getId().equals(t.getId())).findFirst().orElse(null);
        assertNotNull(updated);
        assertTrue(updated.isCompleted());

        dao.deleteCompleted();
        List<CleaningTask> empty = dao.listAll();
        assertTrue(empty.isEmpty());
    }

    @Test
    void testOrderingByDue() throws Exception {
        CleaningTask a = new CleaningTask("A", LocalDate.of(2025, 11, 1), null, null, false);
        CleaningTask b = new CleaningTask("B", null, null, null, false);
        CleaningTask c = new CleaningTask("C", LocalDate.of(2025, 10, 1), null, null, false);
        dao.insert(a);
        dao.insert(b);
        dao.insert(c);

        List<CleaningTask> list = dao.listAll();
        // expected order: tasks with non-null due sorted ascending (c, a), then null due B last
        assertEquals("C", list.get(0).getTitle());
        assertEquals("A", list.get(1).getTitle());
        assertEquals("B", list.get(2).getTitle());
    }

    // ----- zusätzliche Tests -----

    @Test
    void testInsertWithBlankAssignedToBecomesNullOnLoad() throws Exception {
        CleaningTask t = new CleaningTask("Fenster", LocalDate.of(2025, 7, 1), "   ", null, false);
        dao.insert(t);
        assertNotNull(t.getId());

        List<CleaningTask> all = dao.listAll();
        CleaningTask loaded = all.stream().filter(x -> x.getId().equals(t.getId())).findFirst().orElse(null);
        assertNotNull(loaded);
        assertNull(loaded.getAssignedTo(), "Whitespace assigned_to muss als null interpretiert werden");
    }

    @Test
    void testDirectInsertWithEmptyStringDueIsParsedAsNull() throws Exception {
        // direkte DB-Einfügung mit leerer Zeichenkette als due
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = conn.prepareStatement("INSERT INTO cleaning_tasks(title, due, assigned_to, recurrence, urgent, completed) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, "LeereDue");
            ps.setString(2, ""); // leerer String
            ps.setString(3, null);
            ps.setString(4, null);
            ps.setInt(5, 0);
            ps.setInt(6, 0);
            ps.executeUpdate();
        }

        List<CleaningTask> all = dao.listAll();
        CleaningTask loaded = all.stream().filter(x -> "LeereDue".equals(x.getTitle())).findFirst().orElse(null);
        assertNotNull(loaded, "Eintrag mit leerer due-Zeichenkette sollte vorhanden sein");
        assertNull(loaded.getDue(), "Leere due-Zeichenkette muss als null interpretiert werden");
    }

    @Test
    void testDeleteCompletedOnlyRemovesCompletedTasks() throws Exception {
        CleaningTask done = new CleaningTask("Erledigt", null, null, null, false);
        CleaningTask todo = new CleaningTask("Offen", null, null, null, false);
        dao.insert(done);
        dao.insert(todo);

        // markiere nur den ersten als erledigt
        done.setCompleted(true);
        dao.update(done);

        dao.deleteCompleted();

        List<CleaningTask> remaining = dao.listAll();
        assertEquals(1, remaining.size());
        assertEquals("Offen", remaining.get(0).getTitle());
    }

    @Test
    void testUpdateNonExistingIdDoesNotThrowAndDoesNotCreate() throws Exception {
        // Erzeuge ein Objekt mit gesetzter, aber nicht in DB existierender ID
        CleaningTask ghost = new CleaningTask("Ghost", null, null, null, false);
        ghost.setId(99999); // nicht existent
        // darf keine Exception werfen
        dao.update(ghost);

        // DB muss weiterhin leer sein
        List<CleaningTask> all = dao.listAll();
        assertTrue(all.isEmpty(), "Update einer nicht vorhandenen ID darf keine Einträge erzeugen");
    }

    @Test
    void testUrgentAndCompletedFlagsPersist() throws Exception {
        CleaningTask flags = new CleaningTask("Flags", LocalDate.of(2025, 8, 8), "bob", null, true);
        flags.setCompleted(true);
        dao.insert(flags);

        List<CleaningTask> all = dao.listAll();
        CleaningTask loaded = all.stream().filter(x -> x.getId().equals(flags.getId())).findFirst().orElse(null);
        assertNotNull(loaded);
        assertTrue(loaded.isUrgent(), "urgent Flag muss persistiert werden");
        assertTrue(loaded.isCompleted(), "completed Flag muss persistiert werden");
    }
}