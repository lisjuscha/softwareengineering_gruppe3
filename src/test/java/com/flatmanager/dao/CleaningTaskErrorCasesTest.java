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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CleaningTaskErrorCasesTest {

    private final CleaningTaskDao dao = new CleaningTaskDao();
    private static final String DB_FILE = "target/cleaning_errorcases.db";

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
    void testInsertWithNullTitleThrowsSQLException() {
        CleaningTask t = new CleaningTask(null, LocalDate.now(), "u", null, false);
        assertThrows(SQLException.class, () -> {
            try {
                dao.insert(t);
            } catch (SQLException e) {
                // expected - rethrow to satisfy assertThrows
                throw e;
            }
        });
    }

    @Test
    void testListAllThrowsOnMalformedDate() throws Exception {
        // Directly insert a malformed date string into the DB
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = conn.prepareStatement("INSERT INTO cleaning_tasks(title, due, assigned_to, recurrence, urgent, completed) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, "BadDate");
            ps.setString(2, "not-a-date");
            ps.setString(3, null);
            ps.setString(4, null);
            ps.setInt(5, 0);
            ps.setInt(6, 0);
            ps.executeUpdate();
        }

        // listAll should now throw a DateTimeParseException when trying to parse the malformed date
        assertThrows(DateTimeParseException.class, () -> dao.listAll());
    }

    @Test
    void testInitThrowsOnInvalidJdbcUrl() {
        // set an invalid JDBC URL and ensure dao.init throws SQLException when trying to create table
        System.setProperty("db.url", "jdbc:invalid:url");
        DatabaseManager.closeConnection();
        assertThrows(SQLException.class, () -> dao.init());

        // restore a valid url for cleanup in afterEach
        System.setProperty("db.url", "jdbc:sqlite:" + new File(DB_FILE).getAbsolutePath());
    }

    @Test
    void testDeleteCompletedThrowsWhenTableDropped() throws Exception {
        // insert one task and mark completed
        CleaningTask t = new CleaningTask("X", null, "u", null, false);
        dao.insert(t);
        t.setCompleted(true);
        dao.update(t);

        // Drop the table using a direct connection
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = conn.prepareStatement("DROP TABLE cleaning_tasks")) {
            ps.execute();
        }

        // Now deleteCompleted should throw SQLException because the table is missing
        assertThrows(SQLException.class, () -> dao.deleteCompleted());
    }

    @Test
    void testUpdateWithNullTitleThrows() throws Exception {
        CleaningTask t = new CleaningTask("Tmp", LocalDate.now(), null, null, false);
        dao.insert(t);

        // set title to null and expect update to fail due to NOT NULL constraint
        t.setTitle(null);
        assertThrows(SQLException.class, () -> dao.update(t));
    }
}

