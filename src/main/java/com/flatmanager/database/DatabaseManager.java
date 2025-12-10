package com.flatmanager.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseManager {

    private static volatile Connection connection;
    private static volatile boolean pragmasApplied = false;

    private DatabaseManager() {
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = createConnection();
        }
        return connection;
    }

    private static Connection createConnection() throws SQLException {
        String url = System.getenv().getOrDefault("DB_URL", System.getProperty("db.url", "jdbc:sqlite:flatmanager.db"));

        // Debug: wer fordert die Connection an? (kurzes Stacktrace)
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.err.println("[DatabaseManager] createConnection called -> url=" + url);
        for (int i = 2; i < Math.min(st.length, 8); i++) { // überspringe getStackTrace + aktuelle Methode
            System.err.println("\t at " + st[i]);
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }

        Connection conn = DriverManager.getConnection(url);
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
        }

        // PRAGMA nur einmal pro JVM-Verbindung setzen
        synchronized (DatabaseManager.class) {
            if (!pragmasApplied) {
                try (Statement s = conn.createStatement()) {
                    s.execute("PRAGMA journal_mode = WAL");
                    s.execute("PRAGMA synchronous = NORMAL");
                    s.execute("PRAGMA busy_timeout = 5000");
                    pragmasApplied = true;
                    System.err.println("[DatabaseManager] PRAGMAS applied");
                } catch (SQLException e) {
                    System.err.println("[DatabaseManager] PRAGMA setup failed: " + e.getMessage());
                }
            } else {
                System.err.println("[DatabaseManager] PRAGMAS already applied");
            }
        }

        // Stelle sicher, dass ein Default-Admin vorhanden ist (Benutzer/Passwort im Klartext)
        try {
            ensureDefaultAdmin(conn);
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ensureDefaultAdmin failed: " + e.getMessage());
        }

        System.err.println("[DatabaseManager] Connected -> url=" + url + " / conn=" + conn);
        return conn;
    }

    public static synchronized void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
            pragmasApplied = false;
            System.err.println("[DatabaseManager] Connection closed");
        }
    }

    // Erstellt oder aktualisiert einen User. Versucht mehrere Update-Strategien, dann Insert.
    public static boolean createOrUpdateUser(String username, String password, String name) {
        if (username == null || username.isBlank()) return false;
        try {
            Connection conn = getConnection(); // nicht in try-with-resources, damit die geteilte Connection nicht geschlossen wird

            // Versuch 1: Update über username (häufigster Fall)
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET password = ?, name = ? WHERE username = ? COLLATE NOCASE")) {
                upd.setString(1, password);
                upd.setString(2, name);
                upd.setString(3, username);
                int updated = upd.executeUpdate();
                if (updated > 0) {
                    System.err.println("[DatabaseManager] Updated user (by username): " + username);
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Update by username failed: " + e.getMessage());
            }

            // Versuch 2: Fallback-Update über name (falls Schema anders)
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET password = ? WHERE name = ? COLLATE NOCASE")) {
                upd.setString(1, password);
                upd.setString(2, username);
                int updated = upd.executeUpdate();
                if (updated > 0) {
                    System.err.println("[DatabaseManager] Updated user (by name): " + username);
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Update by name failed: " + e.getMessage());
            }

            // Versuch 3: Insert mit den Standardspalten (falls vorhanden)
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO users (username, password, name) VALUES (?, ?, ?)")) {
                ins.setString(1, username);
                ins.setString(2, password);
                ins.setString(3, name);
                ins.executeUpdate();
                System.err.println("[DatabaseManager] Inserted user: " + username);
                return true;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Insert user failed: " + e.getMessage());
            }

            // Optionaler Fallback: baue dynamisch INSERT je nach vorhandenen Spalten
            boolean hasUsername = false;
            boolean hasName = false;
            boolean hasPassword = false;
            try (PreparedStatement p = getConnection().prepareStatement("PRAGMA table_info(users)");
                 ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("name");
                    if ("username".equalsIgnoreCase(colName)) hasUsername = true;
                    if ("name".equalsIgnoreCase(colName)) hasName = true;
                    if ("password".equalsIgnoreCase(colName)) hasPassword = true;
                }
            } catch (SQLException ignored) {
            }

            List<String> cols = new ArrayList<>();
            List<String> vals = new ArrayList<>();
            if (hasUsername) cols.add("username");
            if (hasPassword) cols.add("password");
            if (hasName) cols.add("name");
            if (!cols.isEmpty()) {
                for (int i = 0; i < cols.size(); i++) vals.add("?");
                String sql = String.format("INSERT INTO users (%s) VALUES (%s)", String.join(", ", cols), String.join(", ", vals));
                try (PreparedStatement ins = conn.prepareStatement(sql)) {
                    int idx = 1;
                    if (hasUsername) ins.setString(idx++, username);
                    if (hasPassword) ins.setString(idx++, password);
                    if (hasName) ins.setString(idx++, name);
                    ins.executeUpdate();
                    System.err.println("[DatabaseManager] Inserted user (dynamic): " + username);
                    return true;
                } catch (SQLException e) {
                    System.err.println("[DatabaseManager] Dynamic insert failed: " + e.getMessage());
                }
            }

            return false;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] createOrUpdateUser failed: " + e.getMessage());
            return false;
        }
    }

    // Legt Tabelle `users` an (falls nicht vorhanden), erkennt vorhandene Spalten
    // und sorgt dafür, dass ein Benutzer Admin/Admin (Klartext) existiert.
    private static void ensureDefaultAdmin(Connection conn) throws SQLException {
        // Erzeuge Tabelle falls nicht vorhanden (minimal)
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT)");
        }

        // Prüfe vorhandene Spalten und NOT NULL-Status für 'name'
        boolean hasUsername = false;
        boolean hasName = false;
        boolean hasPassword = false;
        boolean nameNotNull = false;

        try (PreparedStatement p = conn.prepareStatement("PRAGMA table_info(users)");
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                String colName = rs.getString("name");
                int notnull = rs.getInt("notnull");
                if ("username".equalsIgnoreCase(colName)) hasUsername = true;
                if ("name".equalsIgnoreCase(colName)) {
                    hasName = true;
                    if (notnull == 1) nameNotNull = true;
                }
                if ("password".equalsIgnoreCase(colName)) hasPassword = true;
            }
        } catch (SQLException ignored) {
            // PRAGMA kann auf manchen JDBC-Treibern anders laufen; wir fahren fort
        }

        // Falls notwendig, füge username/name/password-Spalten hinzu (ohne NOT NULL/UNIQUE)
        try (Statement st = conn.createStatement()) {
            if (!hasUsername && !hasName) {
                try {
                    st.execute("ALTER TABLE users ADD COLUMN username TEXT");
                    hasUsername = true;
                } catch (SQLException ignored) {
                }
            }
            if (!hasPassword) {
                try {
                    st.execute("ALTER TABLE users ADD COLUMN password TEXT");
                    hasPassword = true;
                } catch (SQLException ignored) {
                }
            }
            if (!hasName) {
                try {
                    st.execute("ALTER TABLE users ADD COLUMN name TEXT");
                    hasName = true;
                    nameNotNull = false;
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ignored) {
        }

        final String userCol = hasUsername ? "username" : (hasName ? "name" : "username");
        final String passCol = "password";
        final String admin = "Admin";
        final String adminDisplayName = "Administrator";

        // Prüfe ob Admin existiert (case-insensitive)
        String selectSql = hasName
                ? String.format("SELECT %s, name FROM users WHERE %s = ? COLLATE NOCASE", passCol, userCol)
                : String.format("SELECT %s FROM users WHERE %s = ? COLLATE NOCASE", passCol, userCol);

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, admin);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // nicht vorhanden -> einfügen
                    List<String> cols = new ArrayList<>();
                    cols.add(userCol);
                    cols.add(passCol);
                    if (hasName && !cols.contains("name")) cols.add("name");

                    String colList = String.join(", ", cols);
                    String placeholders = String.join(", ", cols.stream().map(c -> "?").toArray(String[]::new));
                    String insertSql = String.format("INSERT INTO users (%s) VALUES (%s)", colList, placeholders);

                    try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                        int idx = 1;
                        ins.setString(idx++, admin);           // userCol
                        ins.setString(idx++, admin);           // passCol
                        if (hasName && cols.contains("name")) {
                            ins.setString(idx++, adminDisplayName);
                        }
                        ins.executeUpdate();
                        System.err.println("[DatabaseManager] Default Admin inserted -> " + insertSql);
                    } catch (SQLException e) {
                        System.err.println("[DatabaseManager] Insert Admin failed: " + e.getMessage());
                    }
                } else {
                    String storedPass = rs.getString(1);
                    String storedName = null;
                    if (hasName && rs.getMetaData().getColumnCount() >= 2) {
                        try {
                            storedName = rs.getString("name");
                        } catch (SQLException ignored) {
                        }
                    }

                    boolean needUpdate = false;
                    if (storedPass == null || !admin.equals(storedPass)) needUpdate = true;
                    if (hasName && nameNotNull && (storedName == null || storedName.isBlank())) needUpdate = true;

                    if (needUpdate) {
                        // Update password (und optional name) für den Admin-Eintrag
                        List<String> setParts = new ArrayList<>();
                        setParts.add(passCol + " = ?");
                        if (hasName) setParts.add("name = ?");
                        String updateSql = String.format("UPDATE users SET %s WHERE %s = ? COLLATE NOCASE", String.join(", ", setParts), userCol);

                        try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                            int idx = 1;
                            upd.setString(idx++, admin); // neues Passwort
                            if (hasName) {
                                upd.setString(idx++, adminDisplayName);
                            }
                            upd.setString(idx, admin); // WHERE parameter
                            int updated = upd.executeUpdate();
                            System.err.println("[DatabaseManager] Admin updated, rows=" + updated + " -> " + updateSql);
                        } catch (SQLException e) {
                            System.err.println("[DatabaseManager] Update Admin failed: " + e.getMessage());
                        }
                    } else {
                        System.err.println("[DatabaseManager] Admin user exists with expected password/name");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] check Admin existence failed: " + e.getMessage());
        }

        // Debug: Liste alle User (robust gegen fehlende Spalten)
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, username, name, password FROM users")) {
            System.err.println("[DatabaseManager] users in DB:");
            while (rs.next()) {
                int id = rs.getInt("id");
                String u = "";
                try {
                    u = rs.getString("username");
                } catch (SQLException ignore) {
                }
                String n = "";
                try {
                    n = rs.getString("name");
                } catch (SQLException ignore) {
                }
                String p = "";
                try {
                    p = rs.getString("password");
                } catch (SQLException ignore) {
                }
                System.err.println("\t" + id + " | username=" + u + " | name=" + n + " | password=" + p);
            }
        } catch (SQLException ignored) {
        }
    }
}