package com.flatmanager.database;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
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
        
        // Check that essential tables exist (do not assume a default admin user is present)
        stmt.executeQuery("SELECT * FROM users LIMIT 1");
        // Check other tables exist
        stmt.executeQuery("SELECT * FROM shopping_items LIMIT 1");
        stmt.executeQuery("SELECT * FROM budget_transactions LIMIT 1");
    }
}
