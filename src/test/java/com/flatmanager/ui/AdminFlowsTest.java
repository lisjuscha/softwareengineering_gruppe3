package com.flatmanager.ui;

import com.flatmanager.storage.Database;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class AdminFlowsTest {
    private static final String DB_FILE = "target/admin_flows_test.db";

    @BeforeAll
    public static void initToolkit() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignore) {}
    }

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) Files.delete(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            // create minimal users table (initial schema)
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS users (username TEXT UNIQUE)");
            }
        }
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    @Test
    public void testEnsureColumnsAndHasColumnResolveName() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            // initially only username exists
            Method hasColumn = AdminCreateUserDialog.class.getDeclaredMethod("hasColumn", java.sql.Connection.class, String.class);
            hasColumn.setAccessible(true);
            assertTrue((Boolean) hasColumn.invoke(null, c, "username"));
            assertFalse((Boolean) hasColumn.invoke(null, c, "is_admin"));

            Method ensureIsAdmin = AdminCreateUserDialog.class.getDeclaredMethod("ensureIsAdminColumn", java.sql.Connection.class);
            ensureIsAdmin.setAccessible(true);
            ensureIsAdmin.invoke(null, c);
            assertTrue((Boolean) hasColumn.invoke(null, c, "is_admin"));

            Method ensurePw = AdminCreateUserDialog.class.getDeclaredMethod("ensurePasswordColumn", java.sql.Connection.class);
            ensurePw.setAccessible(true);
            ensurePw.invoke(null, c);
            assertTrue((Boolean) hasColumn.invoke(null, c, "password"));

            Method resolveName = AdminCreateUserDialog.class.getDeclaredMethod("resolveNameColumn", java.sql.Connection.class);
            resolveName.setAccessible(true);
            String col = (String) resolveName.invoke(null, c);
            assertNotNull(col);
            assertTrue(col.equalsIgnoreCase("username") || col.equalsIgnoreCase("name"));
        }
    }

    @Test
    public void testHashPasswordProducesSha256() throws Exception {
        Method hash = AdminCreateUserDialog.class.getDeclaredMethod("hashPassword", String.class);
        hash.setAccessible(true);
        String out = (String) hash.invoke(null, "secret123");
        // verify against MessageDigest
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest("secret123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); for (byte b : digest) sb.append(String.format("%02x", b));
        assertEquals(sb.toString(), out);
    }

    @Test
    public void testLoadUsernamesExcludesCurrentAdmin() throws Exception {
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO users (username) VALUES (?)")) {
                ps.setString(1, "alice"); ps.executeUpdate();
                ps.setString(1, "bob"); ps.executeUpdate();
            }
        }

        Method loadUsers = AdminDeleteUserDialog.class.getDeclaredMethod("loadUsernames", javafx.stage.Window.class, String.class);
        loadUsers.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<String> names = (java.util.List<String>) loadUsers.invoke(null, null, "bob");
        assertTrue(names.stream().anyMatch(s -> s.equalsIgnoreCase("alice")));
        assertFalse(names.stream().anyMatch(s -> s.equalsIgnoreCase("bob")));
    }

    @Test
    public void testAdminToolbarIsAdminDetectionAndNode() throws Exception {
        // insert non-admin user
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE users ADD COLUMN is_admin INTEGER DEFAULT 0");
                st.execute("INSERT INTO users (username, is_admin) VALUES ('u1', 0)");
                st.execute("INSERT INTO users (username, is_admin) VALUES ('u2', 1)");
            }
        }

        Method isAdmin = AdminToolbar.class.getDeclaredMethod("isAdmin", String.class);
        isAdmin.setAccessible(true);
        assertFalse((Boolean) isAdmin.invoke(null, "u1"));
        assertTrue((Boolean) isAdmin.invoke(null, "u2"));
        // special-case username 'admin'
        assertTrue((Boolean) isAdmin.invoke(null, "admin"));

        // verify settingsNode returns placeholder for non-admin and Button for admin
        Node n1 = AdminToolbar.settingsNode("u1");
        assertNotNull(n1);
        assertFalse(n1.isVisible()); // placeholder is invisible

        Node n2 = AdminToolbar.settingsNode("u2");
        assertNotNull(n2);
        assertTrue(n2 instanceof Button);
    }
}

