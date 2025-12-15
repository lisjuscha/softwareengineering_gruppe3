package com.flatmanager.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

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

        // PRAGMA nur einmal pro JVM-Verbindung setzen (journal_mode jetzt konfigurierbar)
        synchronized (DatabaseManager.class) {
            if (!pragmasApplied) {
                try (Statement s = conn.createStatement()) {
                    String journalMode = System.getenv().getOrDefault("DB_JOURNAL_MODE",
                            System.getProperty("db.journal_mode", "DELETE"));
                    s.execute("PRAGMA journal_mode = " + journalMode);
                    s.execute("PRAGMA synchronous = NORMAL");
                    s.execute("PRAGMA busy_timeout = 5000");
                    pragmasApplied = true;
                    System.err.println("[DatabaseManager] PRAGMAS applied (journal_mode=" + journalMode + ")");
                } catch (SQLException e) {
                    System.err.println("[DatabaseManager] PRAGMA setup failed: " + e.getMessage());
                }
            } else {
                System.err.println("[DatabaseManager] PRAGMAS already applied");
            }
        }

        // Stelle sicher, dass ein Default-Admin vorhanden ist (gehashedes Passwort, is_admin gesetzt)
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
    // und sorgt dafür, dass ein Benutzer admin/admin existiert (Passwort gehashed, is_admin gesetzt).
    private static void ensureDefaultAdmin(Connection conn) throws SQLException {
        // Erzeuge Tabelle falls nicht vorhanden (minimal)
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT)");
        }

        // Prüfe vorhandene Spalten und NOT NULL-Status für 'name'
        boolean hasUsername = false;
        boolean hasName = false;
        boolean hasPassword = false;
        boolean hasIsAdmin = false;
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
                if ("is_admin".equalsIgnoreCase(colName)) hasIsAdmin = true;
            }
        } catch (SQLException ignored) {
            // PRAGMA kann auf manchen JDBC-Treibern anders laufen; wir fahren fort
        }

        // Falls notwendig, Spalten anlegen
        try (Statement st = conn.createStatement()) {
            if (!hasUsername) {
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
            if (!hasIsAdmin) {
                try {
                    st.execute("ALTER TABLE users ADD COLUMN is_admin INTEGER DEFAULT 0");
                    hasIsAdmin = true;
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ignored) {
        }

        final String userCol = hasUsername ? "username" : (hasName ? "name" : "username");
        final String passCol = "password";
        final String adminUser = "admin";
        final String adminDisplayName = "Administrator";
        final String adminHash = hashPassword("Admin");

        // Prüfe ob Admin existiert (case-insensitive) und lese is_admin
        String selectSql = String.format("SELECT %s, %s FROM users WHERE %s = ? COLLATE NOCASE", passCol, "is_admin", userCol);
        boolean found = false;
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, adminUser);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    found = true;
                    Object val = null;
                    try {
                        val = rs.getObject("is_admin");
                    } catch (SQLException ignored) {
                    }
                    boolean isAdmin = false;
                    if (val instanceof Number) isAdmin = ((Number) val).intValue() == 1;
                    else if (val != null)
                        isAdmin = "1".equals(val.toString()) || "true".equalsIgnoreCase(val.toString());

                    String storedPass = null;
                    try {
                        storedPass = rs.getString(passCol);
                    } catch (SQLException ignored) {
                    }

                    // Wenn Passwort nicht gehashed ist oder is_admin nicht gesetzt -> update
                    boolean needUpdate = false;
                    if (storedPass == null || !storedPass.equalsIgnoreCase(adminHash)) needUpdate = true;
                    if (!isAdmin) needUpdate = true;

                    if (needUpdate) {
                        String updateSql = String.format("UPDATE users SET %s = ?, is_admin = 1%s WHERE %s = ? COLLATE NOCASE",
                                passCol, hasName ? ", name = ?" : "", userCol);
                        try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                            int idx = 1;
                            upd.setString(idx++, adminHash);
                            if (hasName) upd.setString(idx++, adminDisplayName);
                            upd.setString(idx, adminUser);
                            int updated = upd.executeUpdate();
                            System.err.println("[DatabaseManager] Admin updated rows=" + updated);
                        } catch (SQLException e) {
                            System.err.println("[DatabaseManager] Update Admin failed: " + e.getMessage());
                        }
                    } else {
                        System.err.println("[DatabaseManager] Admin exists and is admin");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] check Admin existence failed: " + e.getMessage());
        }

        // Wenn nicht gefunden -> Insert mit is_admin=1 und gehashter Passwort
        if (!found) {
            List<String> cols = new ArrayList<>();
            cols.add(userCol);
            cols.add(passCol);
            cols.add("is_admin");
            if (hasName) cols.add("name");

            String colList = String.join(", ", cols);
            String placeholders = String.join(", ", cols.stream().map(c -> "?").toArray(String[]::new));
            String insertSql = String.format("INSERT INTO users (%s) VALUES (%s)", colList, placeholders);

            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                int idx = 1;
                ins.setString(idx++, adminUser);
                ins.setString(idx++, adminHash);
                ins.setInt(idx++, 1);
                if (hasName) ins.setString(idx++, adminDisplayName);
                ins.executeUpdate();
                System.err.println("[DatabaseManager] Default Admin inserted -> is_admin=1");
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Insert Admin failed: " + e.getMessage());
            }
        }

        // Debug: Liste alle User (robust gegen fehlende Spalten)
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, username, name, password, is_admin FROM users")) {
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
                String ia = "";
                try {
                    Object o = rs.getObject("is_admin");
                    ia = o == null ? "null" : o.toString();
                } catch (SQLException ignore) {
                }
                System.err.println("\t" + id + " | username=" + u + " | name=" + n + " | password=" + p + " | is_admin=" + ia);
            }
        } catch (SQLException ignored) {
        }
    }

    /* ----------------- Neue Methoden: Benutzer auflisten / loggen / suchen ----------------- */

    public static final class UserInfo {
        public final int id;
        public final String username;
        public final String name;
        public final boolean isAdmin;
        public final String passwordHash; // maskiert, sofern nicht anders angefragt

        public UserInfo(int id, String username, String name, boolean isAdmin, String passwordHash) {
            this.id = id;
            this.username = username;
            this.name = name;
            this.isAdmin = isAdmin;
            this.passwordHash = passwordHash;
        }

        @Override
        public String toString() {
            return String.format("id=%d | username=%s | name=%s | isAdmin=%s | password=%s",
                    id, username, name, isAdmin, passwordHash);
        }
    }

    /**
     * Liefert alle Benutzer; Passwort standardmäßig maskiert.
     */
    public static List<UserInfo> listUsers() {
        return listUsers(false);
    }

    /**
     * Liefert alle Benutzer; includePasswordHash = true gibt den (gehashten) Wert zurück.
     */
    public static List<UserInfo> listUsers(boolean includePasswordHash) {
        List<UserInfo> out = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement s = conn.createStatement()) {

            // Erkenne vorhandene Spalten
            boolean hasId = false, hasUsername = false, hasName = false, hasPassword = false, hasIsAdmin = false;
            try (ResultSet rs = s.executeQuery("PRAGMA table_info(users)")) {
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("id".equalsIgnoreCase(col)) hasId = true;
                    if ("username".equalsIgnoreCase(col)) hasUsername = true;
                    if ("name".equalsIgnoreCase(col)) hasName = true;
                    if ("password".equalsIgnoreCase(col)) hasPassword = true;
                    if ("is_admin".equalsIgnoreCase(col)) hasIsAdmin = true;
                }
            } catch (SQLException ignored) {
            }

            // Baue SELECT dynamisch
            List<String> cols = new ArrayList<>();
            if (hasId) cols.add("id");
            if (hasUsername) cols.add("username");
            if (hasName) cols.add("name");
            if (hasPassword) cols.add("password");
            if (hasIsAdmin) cols.add("COALESCE(is_admin, 0) AS is_admin");
            String sql = "SELECT " + (cols.isEmpty() ? "*" : String.join(", ", cols)) + " FROM users";

            try (ResultSet rs = s.executeQuery(sql)) {
                while (rs.next()) {
                    int id = hasId ? rs.getInt("id") : -1;
                    String u = hasUsername ? safeGet(rs, "username") : null;
                    String n = hasName ? safeGet(rs, "name") : null;
                    String p = hasPassword ? safeGet(rs, "password") : null;

                    boolean isAdmin = false;
                    if (hasIsAdmin) {
                        Object val = null;
                        try {
                            val = rs.getObject("is_admin");
                        } catch (SQLException ignored) {
                        }
                        if (val instanceof Number) {
                            isAdmin = ((Number) val).intValue() == 1;
                        } else if (val != null) {
                            String sVal = val.toString().trim();
                            isAdmin = "1".equals(sVal) || "true".equalsIgnoreCase(sVal);
                        }
                    } else {
                        isAdmin = "admin".equalsIgnoreCase(u) || "admin".equalsIgnoreCase(n);
                    }

                    if (p != null && !includePasswordHash) p = maskHash(p);
                    out.add(new UserInfo(id, u, n, isAdmin, p));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] listUsers failed: " + e.getMessage());
        }
        return out;
    }

    /**
     * Suche Benutzer nach Teilstring in username oder name (case-insensitive).
     * query - Suchstring (null/leer => leere Liste)
     * includePasswordHash - true => gibt das gehashte Passwort zurück (wenn vorhanden), sonst maskiert
     * onlyAdmins - null = alle, true = nur is_admin=1, false = nur is_admin=0 (wirkt nur wenn Spalte existiert)
     */
    public static List<UserInfo> searchUsers(String query) {
        return searchUsers(query, false, null);
    }

    public static List<UserInfo> searchUsers(String query, boolean includePasswordHash, Boolean onlyAdmins) {
        List<UserInfo> out = new ArrayList<>();
        if (query == null || query.isBlank()) return out;
        String like = "%" + query.trim() + "%";

        try (Connection conn = getConnection()) {
            boolean hasId = false, hasUsername = false, hasName = false, hasPassword = false, hasIsAdmin = false;
            try (PreparedStatement p = conn.prepareStatement("PRAGMA table_info(users)");
                 ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("id".equalsIgnoreCase(col)) hasId = true;
                    if ("username".equalsIgnoreCase(col)) hasUsername = true;
                    if ("name".equalsIgnoreCase(col)) hasName = true;
                    if ("password".equalsIgnoreCase(col)) hasPassword = true;
                    if ("is_admin".equalsIgnoreCase(col)) hasIsAdmin = true;
                }
            } catch (SQLException ignored) {
            }

            // Baue SELECT
            List<String> cols = new ArrayList<>();
            if (hasId) cols.add("id");
            if (hasUsername) cols.add("username");
            if (hasName) cols.add("name");
            if (hasPassword) cols.add("password");
            if (hasIsAdmin) cols.add("COALESCE(is_admin, 0) AS is_admin");
            String selectCols = cols.isEmpty() ? "*" : String.join(", ", cols);
            StringBuilder sql = new StringBuilder("SELECT " + selectCols + " FROM users WHERE ");

            List<String> whereParts = new ArrayList<>();
            if (hasUsername) whereParts.add("username LIKE ? COLLATE NOCASE");
            if (hasName) whereParts.add("name LIKE ? COLLATE NOCASE");
            if (whereParts.isEmpty()) {
                // Fallback: suche in username/name via COALESCE cast (robust)
                whereParts.add("COALESCE(username, '') LIKE ? COLLATE NOCASE");
                whereParts.add("COALESCE(name, '') LIKE ? COLLATE NOCASE");
            }
            sql.append("(").append(String.join(" OR ", whereParts)).append(")");

            if (hasIsAdmin && onlyAdmins != null) {
                sql.append(" AND COALESCE(is_admin,0) = ?");
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                // Set LIKE params: if both username and name present, set two params; if only one present, still set one param
                if (hasUsername && hasName) {
                    ps.setString(idx++, like);
                    ps.setString(idx++, like);
                } else if (hasUsername && !hasName) {
                    ps.setString(idx++, like);
                } else if (!hasUsername && hasName) {
                    ps.setString(idx++, like);
                } else {
                    // fallback whereParts had two entries COALESCE(...), set both
                    ps.setString(idx++, like);
                    ps.setString(idx++, like);
                }

                if (hasIsAdmin && onlyAdmins != null) {
                    ps.setInt(idx++, onlyAdmins ? 1 : 0);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = hasId ? rs.getInt("id") : -1;
                        String u = hasUsername ? safeGet(rs, "username") : (hasName ? safeGet(rs, "name") : null);
                        String n = hasName ? safeGet(rs, "name") : null;
                        String p = hasPassword ? safeGet(rs, "password") : null;

                        boolean isAdmin = false;
                        if (hasIsAdmin) {
                            Object val = null;
                            try {
                                val = rs.getObject("is_admin");
                            } catch (SQLException ignored) {
                            }
                            if (val instanceof Number) {
                                isAdmin = ((Number) val).intValue() == 1;
                            } else if (val != null) {
                                String sVal = val.toString().trim();
                                isAdmin = "1".equals(sVal) || "true".equalsIgnoreCase(sVal);
                            }
                        } else {
                            isAdmin = "admin".equalsIgnoreCase(u) || "admin".equalsIgnoreCase(n);
                        }

                        if (p != null && !includePasswordHash) p = maskHash(p);
                        out.add(new UserInfo(id, u, n, isAdmin, p));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] searchUsers failed: " + e.getMessage());
        }
        return out;
    }

    /**
     * Loggt die Benutzerliste (Passwörter maskiert).
     */
    public static void logUsers() {
        List<UserInfo> users = listUsers(false);
        System.err.println("[DatabaseManager] users:");
        for (UserInfo u : users) {
            System.err.println("\t" + u.toString());
        }
    }

    /* --- Hilfsmethoden --- */
    private static String safeGet(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException e) {
            return null;
        }
    }

    private static String maskHash(String h) {
        if (h == null) return null;
        if (h.length() <= 8) return "****";
        return h.substring(0, 4) + "..." + h.substring(h.length() - 4);
    }

    // Helfer: SHA-256 Hash (UTF-8, hex lowercase)
    private static String hashPassword(String plain) {
        if (plain == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // Fallback: im Fehlerfall das Klartext-Passwort zurückgeben (nur als letzte Option)
            return plain;
        }
    }
}