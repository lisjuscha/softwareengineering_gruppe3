package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.CleaningTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CleaningTaskDaoTest {

    private final CleaningTaskDao dao = new CleaningTaskDao();

    @BeforeEach
    void before() throws Exception {
        // Use a file-based temporary DB for stability across connections in tests
        java.io.File dbFile = new java.io.File("target/cleaning_test.db");
        if (dbFile.exists()) dbFile.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + dbFile.getAbsolutePath());
        DatabaseManager.closeConnection();
        dao.init();
    }

    @AfterEach
    void after() {
        DatabaseManager.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/cleaning_test.db")); } catch (Exception ignore) {}
    }

    @Test
    void testInsertAndListAll() throws Exception {
        CleaningTask t1 = new CleaningTask("Bad putzen", LocalDate.of(2025,12,1), "alice", "Einmalig", true);
        dao.insert(t1);
        assertNotNull(t1.getId(), "insert should set id");

        List<CleaningTask> list = dao.listAll();
        assertEquals(1, list.size());
        CleaningTask loaded = list.get(0);
        assertEquals("Bad putzen", loaded.getTitle());
        assertEquals(LocalDate.of(2025,12,1), loaded.getDue());
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
        CleaningTask a = new CleaningTask("A", LocalDate.of(2025,11,1), null, null, false);
        CleaningTask b = new CleaningTask("B", null, null, null, false);
        CleaningTask c = new CleaningTask("C", LocalDate.of(2025,10,1), null, null, false);
        dao.insert(a);
        dao.insert(b);
        dao.insert(c);

        List<CleaningTask> list = dao.listAll();
        // expected order: tasks with non-null due sorted ascending (c, a), then null due B last
        assertEquals("C", list.get(0).getTitle());
        assertEquals("A", list.get(1).getTitle());
        assertEquals("B", list.get(2).getTitle());
    }
}
