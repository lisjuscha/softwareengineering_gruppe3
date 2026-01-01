package com.flatmanager.ui;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.storage.Database;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LoginRegistrationEdgeCasesTest {
    private static final String DB_FILE = "target/login_registration_edgecases_test.db";

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
        // Ensure application schema is created via Database/DatabaseManager
        com.flatmanager.storage.Database.init();
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    @Test
    public void testAdminAuthenticationIsCaseInsensitive() throws Exception {
        // create admin with mixed case username
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"))) {
            try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO users (username, password, is_admin) VALUES (?, ?, ?)") ) {
                ps.setString(1, "AdminX");
                Method hash = LoginScreen.class.getDeclaredMethod("hashPassword", String.class);
                hash.setAccessible(true);
                String hashed = (String) hash.invoke(null, "pw");
                ps.setString(2, hashed);
                ps.setInt(3, 1);
                ps.executeUpdate();
            }
        }

        LoginScreen ls = new LoginScreen();
        Method auth = LoginScreen.class.getDeclaredMethod("authenticateAdmin", String.class, String.class);
        auth.setAccessible(true);
        boolean okLower = (Boolean) auth.invoke(ls, "adminx", "pw");
        boolean okUpper = (Boolean) auth.invoke(ls, "ADMINX", "pw");
        assertTrue(okLower);
        assertTrue(okUpper);
    }

    @Test
    public void testCreateHouseholdSkipsEmptyMemberUsernames() throws Exception {
        boolean ok = DatabaseManager.createHouseholdWithAdmin("WG", "master", "pw", List.of(
                new DatabaseManager.UserData("Good","good","g1"),
                new DatabaseManager.UserData("Bad","  ","b1"),
                new DatabaseManager.UserData("AlsoBad",null,"b2")
        ));
        assertTrue(ok);
        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        assertTrue(users.stream().anyMatch(u -> "master".equalsIgnoreCase(u.username) && u.isAdmin));
        assertTrue(users.stream().anyMatch(u -> "good".equalsIgnoreCase(u.username)));
        // ensure empty usernames were ignored
        assertFalse(users.stream().anyMatch(u -> u.username == null || u.username.trim().isEmpty()));
    }

    @Test
    public void testCreateHouseholdHandlesDuplicateMemberUsernamesCaseInsensitive() throws Exception {
        boolean ok = DatabaseManager.createHouseholdWithAdmin("WG2", "main", "pw", List.of(
                new DatabaseManager.UserData("Dup","john","p1"),
                new DatabaseManager.UserData("DupLower","JOHN","p2"),
                new DatabaseManager.UserData("Other","sue","s1")
        ));
        assertTrue(ok);
        List<DatabaseManager.UserInfo> users = DatabaseManager.listUsers();
        // john should exist only once (case-insensitive match), and sue exists
        long johnCount = users.stream().filter(u -> "john".equalsIgnoreCase(u.username)).count();
        assertEquals(1, johnCount);
        assertTrue(users.stream().anyMatch(u -> "sue".equalsIgnoreCase(u.username)));
    }
}
