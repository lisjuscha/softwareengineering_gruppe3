package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminDeleteUserDialogTest {

    private static final String DB_FILE = "target/admin_delete_test.db";

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
    }

    @AfterEach
    public void tearDown() {
        Database.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignore) {}
    }

    private Method getPrivate(String name, Class<?>... params) throws Exception {
        Method m = AdminDeleteUserDialog.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void testTableExistsWhenMissingReturnsFalse() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method tableExists = getPrivate("tableExists", Connection.class, String.class);
            boolean exists = (boolean) tableExists.invoke(null, c, "users");
            assertFalse(exists);
        }
    }

    @Test
    public void testTableExistsWhenPresentReturnsTrue() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
        }
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method tableExists = getPrivate("tableExists", Connection.class, String.class);
            boolean exists = (boolean) tableExists.invoke(null, c, "users");
            assertTrue(exists);
        }
    }

    @Test
    public void testLoadUsernamesExcludesCurrentAdminAndTrims() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "admin"); ps.executeUpdate();
                ps.setString(1, " Alice "); ps.executeUpdate();
                ps.setString(1, "bob"); ps.executeUpdate();
                ps.setString(1, "ADMIN "); ps.executeUpdate();
                ps.setString(1, null); ps.executeUpdate();
            }
        }

        // loadUsernames(Window owner, String currentAdminUsername) - owner may be null
        Method loadUsernames = getPrivate("loadUsernames", javafx.stage.Window.class, String.class);
        @SuppressWarnings("unchecked")
        List<String> users = (List<String>) loadUsernames.invoke(null, null, "Admin");

        // should exclude both 'admin' entries case-insensitively and trim names
        assertFalse(users.contains("admin"));
        assertFalse(users.contains("ADMIN"));
        assertTrue(users.contains("Alice"));
        assertTrue(users.contains("bob"));

        // ordering: SQL ORDER BY username -> after trimming, 'Alice' then 'bob'
        assertEquals("Alice", users.get(0));
        assertEquals("bob", users.get(1));
    }

    @Test
    public void testLoadUsernamesSkipsNulls() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, null); ps.executeUpdate();
                ps.setString(1, "charlie"); ps.executeUpdate();
            }
        }

        Method loadUsernames = getPrivate("loadUsernames", javafx.stage.Window.class, String.class);
        @SuppressWarnings("unchecked")
        List<String> users = (List<String>) loadUsernames.invoke(null, null, null);
        assertEquals(1, users.size());
        assertEquals("charlie", users.get(0));
    }

    @Test
    public void testLoadUsernamesAllAdminsReturnsEmpty() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "Admin"); ps.executeUpdate();
                ps.setString(1, " admin "); ps.executeUpdate();
                ps.setString(1, "ADMIN"); ps.executeUpdate();
            }
        }

        Method loadUsernames = getPrivate("loadUsernames", javafx.stage.Window.class, String.class);
        @SuppressWarnings("unchecked")
        List<String> users = (List<String>) loadUsernames.invoke(null, null, "admin");
        assertTrue(users.isEmpty(), "Wenn alle Einträge der aktuelle Admin sind, sollte die Liste leer sein");
    }

    @Test
    public void testLoadUsernamesWithUnicodeAndVeryLongNames() throws Exception {
        String longName = "x".repeat(800);
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, " José "); ps.executeUpdate();
                ps.setString(1, longName); ps.executeUpdate();
            }
        }

        Method loadUsernames = getPrivate("loadUsernames", javafx.stage.Window.class, String.class);
        @SuppressWarnings("unchecked")
        List<String> users = (List<String>) loadUsernames.invoke(null, null, null);
        assertTrue(users.contains("José"));
        assertTrue(users.contains(longName));
    }

    @Test
    public void testLoadUsernamesPreservesDuplicates() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "dup"); ps.executeUpdate();
                ps.setString(1, " dup "); ps.executeUpdate();
            }
        }

        Method loadUsernames = getPrivate("loadUsernames", javafx.stage.Window.class, String.class);
        @SuppressWarnings("unchecked")
        List<String> users = (List<String>) loadUsernames.invoke(null, null, null);
        // Expect two entries after trimming: "dup" and "dup"
        assertEquals(2, users.size());
        assertEquals("dup", users.get(0));
        assertEquals("dup", users.get(1));
    }

    @Test
    public void testTableExistsIsCaseSensitiveForName() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
        }
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method tableExists = getPrivate("tableExists", Connection.class, String.class);
            boolean existsLower = (boolean) tableExists.invoke(null, c, "users");
            boolean existsUpper = (boolean) tableExists.invoke(null, c, "USERS");
            assertTrue(existsLower);
            // sqlite table names are stored case-sensitively in sqlite_master, so check behavior
            assertFalse(existsUpper);
        }
    }

    @Test
    public void testLoadUsernamesIncludesWhitespaceAsEmptyString() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "   "); ps.executeUpdate();
                ps.setString(1, "\t"); ps.executeUpdate();
                ps.setString(1, "dave"); ps.executeUpdate();
            }
        }

        Method loadUsernames = getPrivate("loadUsernames", javafx.stage.Window.class, String.class);
        @SuppressWarnings("unchecked")
        List<String> users = (List<String>) loadUsernames.invoke(null, null, null);
        // trimmed whitespace entries become empty strings and are added
        assertTrue(users.contains(""));
        assertTrue(users.contains("dave"));
    }
}
