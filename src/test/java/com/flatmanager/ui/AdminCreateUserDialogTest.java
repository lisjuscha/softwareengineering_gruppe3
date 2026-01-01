package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class AdminCreateUserDialogTest {

    private static final String DB_FILE = "target/admin_dialog_test.db";

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignore) {}
    }

    private Method getPrivate(String name, Class<?>... params) throws Exception {
        Method m = AdminCreateUserDialog.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void testHashPasswordProducesExpectedSHA256() throws Exception {
        Method hash = getPrivate("hashPassword", String.class);
        String res = (String) hash.invoke(null, "secret123");

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest("secret123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        String expected = sb.toString();
        assertEquals(expected, res);
    }

    @Test
    public void testHasColumnAndEnsureColumns() throws Exception {
        // create users table with only username column
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE)");
        }

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method hasColumn = getPrivate("hasColumn", Connection.class, String.class);
            boolean hasUsername = (boolean) hasColumn.invoke(null, c, "username");
            boolean hasPassword = (boolean) hasColumn.invoke(null, c, "password");
            boolean hasIsAdmin = (boolean) hasColumn.invoke(null, c, "is_admin");

            assertTrue(hasUsername);
            assertFalse(hasPassword);
            assertFalse(hasIsAdmin);

            // ensure password and is_admin added
            Method ensurePw = getPrivate("ensurePasswordColumn", Connection.class);
            Method ensureAdmin = getPrivate("ensureIsAdminColumn", Connection.class);
            ensurePw.invoke(null, c);
            ensureAdmin.invoke(null, c);

            boolean hasPasswordAfter = (boolean) hasColumn.invoke(null, c, "password");
            boolean hasIsAdminAfter = (boolean) hasColumn.invoke(null, c, "is_admin");
            assertTrue(hasPasswordAfter);
            assertTrue(hasIsAdminAfter);
        }
    }

    @Test
    public void testResolveNameColumnVariants() throws Exception {
        // case 1: username exists
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
        }
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method resolve = getPrivate("resolveNameColumn", Connection.class);
            String col = (String) resolve.invoke(null, c);
            assertEquals("username", col);
        }

        // reset DB
        DatabaseManager.closeConnection();
        File db = new File(DB_FILE);
        if (db.exists()) db.delete();

        // case 2: only name exists
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (name TEXT)");
        }
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method resolve = getPrivate("resolveNameColumn", Connection.class);
            String col = (String) resolve.invoke(null, c);
            assertEquals("name", col);
        }
    }

    @Test
    public void testInsertAfterEnsureColumns() throws Exception {
        // create users table with only username
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE)");
        }

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method ensurePw = getPrivate("ensurePasswordColumn", Connection.class);
            Method ensureAdmin = getPrivate("ensureIsAdminColumn", Connection.class);
            ensurePw.invoke(null, c);
            ensureAdmin.invoke(null, c);

            // hash password via dialog util
            Method hash = getPrivate("hashPassword", String.class);
            String hashed = (String) hash.invoke(null, "pw123");

            String insert = "INSERT INTO users (username, password, is_admin) VALUES (?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, "alice");
                ps.setString(2, hashed);
                ps.setInt(3, 1);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT username, password, is_admin FROM users WHERE username = ?")) {
                ps.setString(1, "alice");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("alice", rs.getString("username"));
                    assertEquals(hashed, rs.getString("password"));
                    assertEquals(1, rs.getInt("is_admin"));
                }
            }
        }
    }

    @Test
    public void testHasColumnOnMissingTableReturnsFalse() throws Exception {
        // no table created
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method hasColumn = getPrivate("hasColumn", Connection.class, String.class);
            boolean exists = (boolean) hasColumn.invoke(null, c, "username");
            // implementation returns false if table missing
            assertFalse(exists);
        }
    }

    // --- zusätzliche Randfalltests ---

    @Test
    public void testEnsurePasswordColumnIdempotent() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
            Method ensurePw = getPrivate("ensurePasswordColumn", Connection.class);
            // call twice, should not throw and column should exist
            ensurePw.invoke(null, c);
            ensurePw.invoke(null, c);

            Method hasColumn = getPrivate("hasColumn", Connection.class, String.class);
            assertTrue((boolean) hasColumn.invoke(null, c, "password"));
        }
    }

    @Test
    public void testEnsureIsAdminColumnIdempotent() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT)");
            Method ensureAdmin = getPrivate("ensureIsAdminColumn", Connection.class);
            ensureAdmin.invoke(null, c);
            ensureAdmin.invoke(null, c);

            Method hasColumn = getPrivate("hasColumn", Connection.class, String.class);
            assertTrue((boolean) hasColumn.invoke(null, c, "is_admin"));
        }
    }

    @Test
    public void testResolveNameWhenBothColumnsExist() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT, name TEXT)");
            Method resolve = getPrivate("resolveNameColumn", Connection.class);
            String col = (String) resolve.invoke(null, c);
            // implementation prefers 'username'
            assertEquals("username", col);
        }
    }

    @Test
    public void testInsertDuplicateUserThrowsSQLException() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, password TEXT, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "bob");
                ps.executeUpdate();
            }
            // second insert should violate UNIQUE
            assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                    ps.setString(1, "bob");
                    ps.executeUpdate();
                }
            });
        }
    }

    @Test
    public void testHasColumnCaseInsensitive() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (UsErNaMe TEXT)");
            Method hasColumn = getPrivate("hasColumn", Connection.class, String.class);
            assertTrue((boolean) hasColumn.invoke(null, c, "username"));
            assertTrue((boolean) hasColumn.invoke(null, c, "USERNAME"));
        }
    }

    @Test
    public void testEnsureColumnsOnMissingTableDoesNotThrow() throws Exception {
        // do not create table; the ensure methods should swallow SQLExceptions
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            Method ensurePw = getPrivate("ensurePasswordColumn", Connection.class);
            Method ensureAdmin = getPrivate("ensureIsAdminColumn", Connection.class);
            // Should not throw when table missing (implementation catches exceptions)
            ensurePw.invoke(null, c);
            ensureAdmin.invoke(null, c);
            // hasColumn should still return false
            Method hasColumn = getPrivate("hasColumn", Connection.class, String.class);
            assertFalse((boolean) hasColumn.invoke(null, c, "password"));
            assertFalse((boolean) hasColumn.invoke(null, c, "is_admin"));
        }
    }
}
