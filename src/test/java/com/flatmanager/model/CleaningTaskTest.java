package com.flatmanager.model;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CleaningTaskTest {

    @Test
    void testConstructorsAndGetters() {
        CleaningTask t = new CleaningTask(5, "Bad putzen", LocalDate.of(2025,12,31), "alice", "Wöchentlich", true, false);
        assertEquals(5, t.getId());
        assertEquals("Bad putzen", t.getTitle());
        assertEquals(LocalDate.of(2025,12,31), t.getDue());
        assertEquals("alice", t.getAssignedTo());
        assertEquals("Wöchentlich", t.getRecurrence());
        assertTrue(t.isCompleted());
        assertFalse(t.isUrgent());
        assertTrue(t.hasAssignee());

        CleaningTask s = new CleaningTask("Flur kehren", null, null, null, true);
        assertNull(s.getId());
        assertEquals("Einmalig", s.getRecurrence(), "default recurrence should be 'Einmalig' when null passed");
        assertFalse(s.hasAssignee());
    }

    @Test
    void testFromResultSet() throws SQLException {
        // Use a real in-memory SQLite DB to obtain a ResultSet compatible with the method under test
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
             java.sql.Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE tmp (id INTEGER, title TEXT, due TEXT, assignedTo TEXT, recurrence TEXT, completed INTEGER, urgent INTEGER)");
            st.executeUpdate("INSERT INTO tmp (id,title,due,assignedTo,recurrence,completed,urgent) VALUES (3,'Müll raus','2025-10-01','bob','Monatlich',1,0)");
            try (java.sql.ResultSet rs = st.executeQuery("SELECT id, title, due, assignedTo, recurrence, completed, urgent FROM tmp")) {
                assertTrue(rs.next());
                CleaningTask t = CleaningTask.fromResultSet(rs);
                assertEquals(3, t.getId());
                assertEquals("Müll raus", t.getTitle());
                assertEquals(LocalDate.of(2025,10,1), t.getDue());
                assertEquals("bob", t.getAssignedTo());
                assertEquals("Monatlich", t.getRecurrence());
                assertTrue(t.isCompleted());
                assertFalse(t.isUrgent());
            }
        }
    }
}

// end of file
