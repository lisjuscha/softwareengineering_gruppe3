package com.flatmanager.database;

import java.sql.*;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:flatmanager.db";
    private static Connection connection = null;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            initializeDatabase();
        }
        return connection;
    }

    private static void initializeDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Users table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL," +
                "name TEXT NOT NULL)"
            );

            // Cleaning schedules table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS cleaning_schedules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "task TEXT NOT NULL," +
                "assigned_to TEXT NOT NULL," +
                "due_date TEXT NOT NULL," +
                "completed INTEGER DEFAULT 0)"
            );

            // Shopping list items table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS shopping_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "item_name TEXT NOT NULL," +
                "quantity TEXT," +
                "added_by TEXT NOT NULL," +
                "purchased INTEGER DEFAULT 0)"
            );

            // Budget transactions table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS budget_transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "description TEXT NOT NULL," +
                "amount REAL NOT NULL," +
                "paid_by TEXT NOT NULL," +
                "date TEXT NOT NULL," +
                "category TEXT NOT NULL)"
            );

            // Insert default user if not exists
            String checkUser = "SELECT COUNT(*) FROM users WHERE username = 'admin'";
            ResultSet rs = stmt.executeQuery(checkUser);
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute(
                    "INSERT INTO users (username, password, name) " +
                    "VALUES ('admin', 'admin', 'Administrator')"
                );
            }
        }
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
