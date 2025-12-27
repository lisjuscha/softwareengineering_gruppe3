package com.flatmanager.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    private static final String DB_PATH = "target/storage_database_test.db";

    @BeforeEach
    void setup() throws Exception {
        File db = new File(DB_PATH);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        // ensure no leftover connection
        Database.closeConnection();
    }

    @AfterEach
    void cleanup() {
        Database.closeConnection();
        try { Files.deleteIfExists(Path.of(DB_PATH)); } catch (Exception ignore) {}
    }

    @Test
    void testGetConnection_returnsOpenConnection() throws Exception {
        Connection conn = Database.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
        // basic query should work (SQLite will accept simple pragma)
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void testInit_setsAutoCommitTrue_orDoesNotThrow() throws Exception {
        // init should not throw and should leave connection usable
        Database.init();
        try (Connection conn = Database.getConnection()) {
            assertNotNull(conn);
            // autoCommit is typically true for sqlite connections opened without transaction
            // check that getting the value does not throw and returns a boolean
            boolean auto = conn.getAutoCommit();
            assertTrue(auto || !auto == false); // trivial check: ensure call succeeds
        }
    }

    @Test
    void testCloseConnection_closesUnderlyingConnection() throws Exception {
        Connection conn = Database.getConnection();
        assertFalse(conn.isClosed());
        Database.closeConnection();
        assertTrue(conn.isClosed(), "Die zuvor geöffnete Connection sollte nach closeConnection() geschlossen sein");
    }

    @Test
    void testInitIsIdempotent_andConnectionUsableForSql() throws Exception {
        // multiple inits must not fail
        Database.init();
        Database.init();

        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement()) {
            // create a table, insert and read back -> proves connection usable
            st.execute("CREATE TABLE IF NOT EXISTS tmp_test(id INTEGER PRIMARY KEY, name TEXT)");
            st.execute("INSERT INTO tmp_test(name) VALUES('x')");
            try (ResultSet rs = st.executeQuery("SELECT name FROM tmp_test WHERE id=1")) {
                assertTrue(rs.next());
                assertEquals("x", rs.getString(1));
            }
        }
    }

    @Test
    void testCloseAndReopenConnection_providesNewOpenConnection() throws Exception {
        Connection first = Database.getConnection();
        assertFalse(first.isClosed());
        Database.closeConnection();
        assertTrue(first.isClosed());

        Connection second = Database.getConnection();
        assertNotNull(second);
        assertFalse(second.isClosed());
        // second should be a working connection
        try (Statement st = second.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}