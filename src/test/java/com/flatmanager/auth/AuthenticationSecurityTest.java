package com.flatmanager.auth;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.storage.Database;
import com.flatmanager.ui.LoginScreen;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationSecurityTest {
    private static final String DB_FILE = "target/auth_security_test.db";

    @BeforeAll
    public static void initToolkit() {
        // initialize JavaFX toolkit to allow instantiation of LoginScreen (which creates Nodes)
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
    }

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) Files.delete(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        // initialize schema
        Database.init();
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    private static String sha256Hex(String input) throws Exception {
        if (input == null) return null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    public void testCreateOrUpdateUserStoresHashedPassword() throws Exception {
        boolean ok = DatabaseManager.createOrUpdateUser("alice", "pw123", "Alice");
        assertTrue(ok);

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT password FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "alice");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String stored = rs.getString("password");
                    assertNotNull(stored);
                    assertNotEquals("pw123", stored, "Password should not be stored in plain text");
                    String expected = sha256Hex("pw123");
                    assertEquals(expected, stored.toLowerCase());
                }
            }
        }
    }

    @Test
    public void testCreateHouseholdWithAdminHashesAdminAndSetsIsAdmin() throws Exception {
        boolean ok = DatabaseManager.createHouseholdWithAdmin(null, "admin", "adminpw", null);
        assertTrue(ok);

        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT password, is_admin FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "admin");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String stored = rs.getString("password");
                    assertNotNull(stored);
                    assertEquals(1, rs.getInt("is_admin"), "Admin flag should be set to 1 for admin user");
                    String expected = sha256Hex("adminpw");
                    assertEquals(expected, stored.toLowerCase());
                }
            }
        }
    }

    @Test
    public void testAuthenticateAdminCorrectAndIncorrectPassword() throws Exception {
        // create admin user
        boolean ok = DatabaseManager.createHouseholdWithAdmin(null, "super", "secretpw", null);
        assertTrue(ok);

        // instantiate LoginScreen (requires FX toolkit, already initialized)
        LoginScreen ls = new LoginScreen();

        // reflectively invoke private authenticateAdmin
        java.lang.reflect.Method m = LoginScreen.class.getDeclaredMethod("authenticateAdmin", String.class, String.class);
        m.setAccessible(true);

        boolean authOk = (boolean) m.invoke(ls, "super", "secretpw");
        assertTrue(authOk, "Correct password should authenticate");

        boolean authBad = (boolean) m.invoke(ls, "super", "wrongpw");
        assertFalse(authBad, "Wrong password should not authenticate");
    }

    @Test
    public void testAuthenticateAdminRejectedWhenStoredPasswordNull() throws Exception {
        // create household with a member without password (password null in DB)
        java.util.List<DatabaseManager.UserData> members = List.of(new DatabaseManager.UserData("Member", "m1", null));
        boolean ok = DatabaseManager.createHouseholdWithAdmin(null, "adm2", "pw", members);
        assertTrue(ok);

        // ensure member m1 has NULL password in DB
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (PreparedStatement ps = c.prepareStatement("SELECT password FROM users WHERE username = ? COLLATE NOCASE")) {
                ps.setString(1, "m1");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertNull(rs.getString("password"));
                }
            }
        }

        // instantiate LoginScreen and try to authenticate member with null password
        LoginScreen ls = new LoginScreen();
        java.lang.reflect.Method m = LoginScreen.class.getDeclaredMethod("authenticateAdmin", String.class, String.class);
        m.setAccessible(true);

        boolean auth = (boolean) m.invoke(ls, "m1", "anything");
        assertFalse(auth, "Authentication should fail when stored password is null");
    }

    @Test
    public void testCreateOrUpdateUserRejectsBlankOrNullUsername() {
        assertFalse(DatabaseManager.createOrUpdateUser(null, "p", "n"));
        assertFalse(DatabaseManager.createOrUpdateUser("  ", "p", "n"));
    }

    @Test
    public void testListUsersMasksPasswordHash() throws Exception {
        boolean ok = DatabaseManager.createOrUpdateUser("bob", "hunter2", "Bob");
        assertTrue(ok);

        java.util.List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        boolean found = false;
        String expectedFull = sha256Hex("hunter2");
        for (DatabaseManager.UserInfo u : users) {
            if ("bob".equalsIgnoreCase(u.username)) {
                found = true;
                assertNotNull(u.passwordHash);
                // masked version should contain ... and prefix/suffix of full hash
                assertTrue(u.passwordHash.contains("..."), "Password hash should be masked with ellipsis");
                String prefix = expectedFull.substring(0, 4);
                String suffix = expectedFull.substring(expectedFull.length() - 4);
                assertTrue(u.passwordHash.startsWith(prefix));
                assertTrue(u.passwordHash.endsWith(suffix));
            }
        }
        assertTrue(found, "bob should appear in user list");
    }
}

