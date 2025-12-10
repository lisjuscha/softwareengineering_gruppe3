package com.flatmanager.storage;

import com.flatmanager.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;

public final class Database {

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    public static void closeConnection() {
        DatabaseManager.closeConnection();
    }

    /**
     * Stellt sicher, dass die Datenbankverbindung aufgebaut und grundlegend konfiguriert ist.
     * Wird von UI-Klassen aufgerufen, die vorher Database.init() erwarten.
     */
    public static void init() throws SQLException {
        Connection conn = getConnection();
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Falls gesetzt nicht möglich -> ignorieren, Verbindung existiert dennoch
        }
    }
}