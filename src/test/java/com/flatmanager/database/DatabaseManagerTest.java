package com.flatmanager.database;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @Test
    void testGetConnection() throws Exception {
        Connection conn = DatabaseManager.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
    }

    @Test
    void testDatabaseTablesExist() throws Exception {
        Connection conn = DatabaseManager.getConnection();
        Statement stmt = conn.createStatement();
        
        // Check if users table exists and has default admin user
        ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE username = 'admin'");
        assertTrue(rs.next(), "Default admin user should exist");
        assertEquals("admin", rs.getString("username"));
        
        // Check if other tables exist
        stmt.executeQuery("SELECT * FROM cleaning_schedules LIMIT 1");
        stmt.executeQuery("SELECT * FROM shopping_items LIMIT 1");
        stmt.executeQuery("SELECT * FROM budget_transactions LIMIT 1");
    }
}
