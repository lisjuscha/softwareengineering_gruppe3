package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;

import static org.junit.jupiter.api.Assertions.*;

public class AdminToolbarTest {

    private static final String DB_FILE = "target/admin_toolbar_test.db";

    @BeforeAll
    public static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignore) {
            // already initialized
        }
    }

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
        Method m = AdminToolbar.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void testIsAdminNullAndDefault() throws Exception {
        Method isAdmin = getPrivate("isAdmin", String.class);
        assertFalse((boolean) isAdmin.invoke(null, (Object) null));
        assertFalse((boolean) isAdmin.invoke(null, "someuser"));
    }

    @Test
    public void testIsAdminByNameAdminLiteral() throws Exception {
        Method isAdmin = getPrivate("isAdmin", String.class);
        assertTrue((boolean) isAdmin.invoke(null, "admin"));
        assertTrue((boolean) isAdmin.invoke(null, " ADMIN "));
    }

    @Test
    public void testIsAdminFromDatabaseFlag() throws Exception {
        // create users table and add alice with is_admin = 1
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, ?)") ) {
                ps.setString(1, "alice"); ps.setInt(2, 1); ps.executeUpdate();
                ps.setString(1, "bob"); ps.setInt(2, 0); ps.executeUpdate();
            }
        }

        Method isAdmin = getPrivate("isAdmin", String.class);
        assertTrue((boolean) isAdmin.invoke(null, "alice"));
        assertFalse((boolean) isAdmin.invoke(null, "bob"));
    }

    @Test
    public void testSettingsNodePlaceholderForNullOrNonAdmin() throws Exception {
        Node n1 = AdminToolbar.settingsNode(null);
        assertTrue(n1 instanceof Region);
        // non-admin user
        Node n2 = AdminToolbar.settingsNode("notexist");
        assertTrue(n2 instanceof Region);
    }

    @Test
    public void testSettingsNodeButtonForAdmin() throws Exception {
        // create DB and add admin user
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, ?)") ) {
                ps.setString(1, "carol"); ps.setInt(2, 1); ps.executeUpdate();
            }
        }

        Node node = AdminToolbar.settingsNode("carol");
        assertNotNull(node);
        assertTrue(node instanceof Button);
        Button b = (Button) node;
        assertTrue(b.getStyleClass().contains("icon-button"));
        // ensure an action is attached (handler present) - we won't execute it
        assertNotNull(b.getOnAction());
        // graphic or text fallback should exist
        assertTrue(b.getGraphic() != null || (b.getText() != null && !b.getText().isBlank()));
    }

    @Test
    public void testIsAdminReturnsFalseOnSqlException() throws Exception {
        // Point db.url to an invalid directory to provoke SQLException on getConnection
        System.setProperty("db.url", "jdbc:sqlite:/this/path/should/not/exist/and/fail/db.sqlite");
        // Ensure DatabaseManager/Database uses this value; call isAdmin and expect false
        Method isAdmin = getPrivate("isAdmin", String.class);
        assertFalse((boolean) isAdmin.invoke(null, "alice"));
    }

    @Test
    public void testSettingsNodeForAdminLiteralDoesNotRequireDb() throws Exception {
        // Even without creating a DB, the literal 'admin' should be treated as admin
        Node node = AdminToolbar.settingsNode("admin");
        assertTrue(node instanceof Button);
    }

    @Test
    public void testIsAdminWhenIsAdminColumnIsNull() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, NULL)")) {
                ps.setString(1, "eve"); ps.executeUpdate();
            }
        }

        Method isAdmin = getPrivate("isAdmin", String.class);
        // NULL should be treated as 0 -> false
        assertFalse((boolean) isAdmin.invoke(null, "eve"));
    }

    @Test
    public void testSettingsNodeReturnsPlaceholderWhenDbBroken() throws Exception {
        // point to invalid DB path so isAdmin will encounter SQLException and return false
        System.setProperty("db.url", "jdbc:sqlite:/this/path/definitely/does/not/exist/db.sqlite");
        Node node = AdminToolbar.settingsNode("carol");
        assertTrue(node instanceof Region, "When DB is unavailable, settingsNode should return the placeholder region");
    }

    @Test
    public void testIsAdminWithNonNumericIsAdminValue() throws Exception {
        // store is_admin as text 'true' (non-numeric)
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, is_admin TEXT)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, ?)") ) {
                ps.setString(1, "gina"); ps.setString(2, "true"); ps.executeUpdate();
            }
        }

        Method isAdmin = getPrivate("isAdmin", String.class);
        // non-numeric value should not be interpreted as 1 -> expect false
        assertFalse((boolean) isAdmin.invoke(null, "gina"));
    }

    @Test
    public void testSettingsNodeWithSpecialCharacterUsername() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, ?)") ) {
                ps.setString(1, "o'reilly"); ps.setInt(2, 1); ps.executeUpdate();
            }
        }

        Node node = AdminToolbar.settingsNode("o'reilly");
        assertTrue(node instanceof Button);
    }

    @Test
    public void testIsAdminCaseSensitivity() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, ?)") ) {
                ps.setString(1, "Geoff"); ps.setInt(2, 1); ps.executeUpdate();
            }
        }

        Method isAdmin = getPrivate("isAdmin", String.class);
        // AdminToolbar.isAdmin uses exact match (no COLLATE NOCASE), so case differs -> false
        assertFalse((boolean) isAdmin.invoke(null, "geoff"));
        assertTrue((boolean) isAdmin.invoke(null, "Geoff"));
    }

    @Test
    public void testSettingsNodeWithWhitespaceOnlyUsername() throws Exception {
        Node node = AdminToolbar.settingsNode("   ");
        assertTrue(node instanceof Region);
    }

    @Test
    public void testButtonTextFallbackWhenIconMissing() throws Exception {
        // create user and ensure resource loading returns null (we can't easily manipulate resource loading here),
        // but we can at least assert that if no graphic is present, text or graphic exists
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, ?)") ) {
                ps.setString(1, "frida"); ps.setInt(2, 1); ps.executeUpdate();
            }
        }

        Node node = AdminToolbar.settingsNode("frida");
        assertTrue(node instanceof Button);
        Button b = (Button) node;
        assertTrue(b.getGraphic() != null || (b.getText() != null && !b.getText().isBlank()));
    }

    @Test
    public void testIsAdminWhenUsersTableMissing() throws Exception {
        // ensure no users table exists
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try { c.createStatement().execute("DROP TABLE IF EXISTS users"); } catch (Exception ignore) {}
        }

        Method isAdmin = getPrivate("isAdmin", String.class);
        assertFalse((boolean) isAdmin.invoke(null, "nobody"));
    }

    @Test
    public void testIsAdminMultipleRowsTakesFirst() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, ?)") ) {
                ps.setString(1, "dupuser"); ps.setInt(2, 0); ps.executeUpdate();
                ps.setString(1, "dupuser"); ps.setInt(2, 1); ps.executeUpdate();
            }
        }

        Method isAdmin = getPrivate("isAdmin", String.class);
        // behavior: it returns the is_admin value of the first selected row; SQLite's default ordering is insertion order
        boolean result = (boolean) isAdmin.invoke(null, "dupuser");
        // result should be false because first row has 0
        assertFalse(result);
    }

    @Test
    public void testSettingsNodeDoesNotModifyDatabase() throws Exception {
        // create table and insert a row; call settingsNode and ensure row count unchanged
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            c.createStatement().execute("CREATE TABLE users (username TEXT UNIQUE, is_admin INTEGER)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, is_admin) VALUES (?, ?)") ) {
                ps.setString(1, "harry"); ps.setInt(2, 1); ps.executeUpdate();
            }
            long before = c.createStatement().executeQuery("SELECT COUNT(*) as cnt FROM users").getLong("cnt");
            // call settingsNode
            Node node = AdminToolbar.settingsNode("harry");
            // verify still same count
            long after = c.createStatement().executeQuery("SELECT COUNT(*) as cnt FROM users").getLong("cnt");
            assertEquals(before, after);
        }
    }

}
