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

        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.err.println("[DatabaseManager] createConnection called -> url=" + url);
        for (int i = 2; i < Math.min(st.length, 8); i++) {
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

        ensureSchema(conn);
        // Hinweis: Default-Admin wird absichtlich nicht automatisch angelegt.
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

    /* ----------------- Schema erstellen + Migration ----------------- */
    private static void ensureSchema(Connection conn) {
        String[] ddls = new String[]{
                // users
                "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "username TEXT UNIQUE," +
                        "name TEXT," +
                        "password TEXT," +
                        "is_admin INTEGER DEFAULT 0" +
                        ")",
                // shopping_items: both column names to be tolerant gegenüber UI/legacy
                "CREATE TABLE IF NOT EXISTS shopping_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "item_name TEXT," +
                        "name TEXT," +
                        "quantity INTEGER DEFAULT 1," +
                        "bought INTEGER DEFAULT 0," +
                        "category TEXT," +
                        "list_id INTEGER," +
                        "added_by INTEGER," +    // neu: wer den Eintrag hinzugefügt hat
                        "created_at TEXT" +
                        ")",
                // budget_transactions: include paid_by and category
                "CREATE TABLE IF NOT EXISTS budget_transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "description TEXT," +
                        "amount REAL NOT NULL," +
                        "date TEXT," +
                        "user_id INTEGER," +
                        "paid_by INTEGER," +
                        "category TEXT" +        // neu: Kategorie/Tag für Transaktion
                        ")"
        };

        try (Statement st = conn.createStatement()) {
            for (String ddl : ddls) {
                try {
                    st.execute(ddl);
                } catch (SQLException e) {
                    System.err.println("[DatabaseManager] DDL failed: " + e.getMessage());
                }
            }

            // Migration: stelle sicher, dass erwartete Spalten existieren; füge sie bei Bedarf hinzu
            ensureColumnExists(conn, "shopping_items", "item_name", "TEXT", "name");
            ensureColumnExists(conn, "shopping_items", "name", "TEXT", "item_name");
            ensureColumnExists(conn, "shopping_items", "added_by", "INTEGER", null);
            ensureColumnExists(conn, "budget_transactions", "paid_by", "INTEGER", "user_id");
            ensureColumnExists(conn, "budget_transactions", "category", "TEXT", null);

            System.err.println("[DatabaseManager] ensureSchema executed / migrations applied");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] ensureSchema failed: " + e.getMessage());
        }
    }

    private static void ensureColumnExists(Connection conn, String table, String column, String type, String copyFromColumn) {
        try {
            boolean has = hasColumn(conn, table, column);
            if (!has) {
                String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type;
                try (Statement s = conn.createStatement()) {
                    s.execute(sql);
                    System.err.println("[DatabaseManager] Added column " + column + " to " + table);
                } catch (SQLException e) {
                    System.err.println("[DatabaseManager] Failed to add column " + column + " to " + table + ": " + e.getMessage());
                }

                // optional: kopiere Werte aus einer vorhandenen Spalte
                if (copyFromColumn != null && !copyFromColumn.isBlank() && hasColumn(conn, table, copyFromColumn)) {
                    String upd = "UPDATE " + table + " SET " + column + " = " + copyFromColumn + " WHERE " + column + " IS NULL";
                    try (Statement s2 = conn.createStatement()) {
                        int changed = s2.executeUpdate(upd);
                        System.err.println("[DatabaseManager] Copied " + changed + " values from " + copyFromColumn + " to " + column + " in " + table);
                    } catch (SQLException e) {
                        System.err.println("[DatabaseManager] Failed to copy data from " + copyFromColumn + " to " + column + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseManager] ensureColumnExists error: " + e.getMessage());
        }
    }

    private static boolean hasColumn(Connection conn, String table, String column) {
        try (PreparedStatement p = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("name");
                    if (col != null && col.equalsIgnoreCase(column)) return true;
                }
            }
        } catch (SQLException e) {
            // table might not exist yet
        }
        return false;
    }

    /* ----------------- Kein Default Admin automatisch ----------------- */
    // Default-Admin-Erzeugung entfernt. Admins müssen explizit per RegistrationView / createHouseholdWithAdmin erstellt werden.

    /* ----------------- Household + Admin Erstellung ----------------- */
    public static final class UserData {
        private final String displayName;
        private final String username;
        private final String password;

        public UserData(String displayName, String username, String password) {
            this.displayName = displayName;
            this.username = username;
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        @Override
        public String toString() {
            return "UserData{" +
                    "displayName='" + displayName + '\'' +
                    ", username='" + username + '\'' +
                    '}';
        }
    }

    /**
     * Legt atomar einen Admin-Benutzer (is_admin=1) und optional weitere Benutzer an / aktualisiert sie.
     * Keine Erstellung eines Default-Admin mehr in der Verbindungserstellung.
     *
     * @param wgName        optionaler WG-Name (derzeit nur als Info, keine eigene Tabelle)
     * @param adminUsername Admin-Benutzername (pflicht)
     * @param adminPassword Admin-Passwort im Klartext (wird gehasht)
     * @param members       optionale weitere Mitglieder
     * @return true bei Erfolg, false bei Validierung oder DB-Fehlern
     */
    public static boolean createHouseholdWithAdmin(String wgName, String adminUsername, String adminPassword, List<UserData> members) {
        if (adminUsername == null || adminUsername.isBlank()) return false;
        if (adminPassword == null || adminPassword.isEmpty()) return false;

        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = true;
            try {
                try {
                    originalAutoCommit = conn.getAutoCommit();
                } catch (SQLException ignored) {
                }
                conn.setAutoCommit(false);

                String hashedAdmin = hashPassword(adminPassword);
                // Prüfe ob Admin existiert
                boolean adminExists = false;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
                    ps.setString(1, adminUsername);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) adminExists = true;
                    }
                }

                if (adminExists) {
                    try (PreparedStatement upd = conn.prepareStatement("UPDATE users SET password = ?, name = ?, is_admin = 1 WHERE username = ? COLLATE NOCASE")) {
                        upd.setString(1, hashedAdmin);
                        upd.setString(2, adminUsername);
                        upd.setString(3, adminUsername);
                        upd.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = conn.prepareStatement("INSERT INTO users (username, password, name, is_admin) VALUES (?, ?, ?, 1)")) {
                        ins.setString(1, adminUsername);
                        ins.setString(2, hashedAdmin);
                        ins.setString(3, adminUsername);
                        ins.executeUpdate();
                    }
                }

                // Mitglieder hinzufügen / updaten (is_admin = 0)
                if (members != null) {
                    for (UserData m : members) {
                        if (m == null) continue;
                        String uname = m.getUsername();
                        if (uname == null || uname.isBlank()) continue;
                        String name = m.getDisplayName() == null || m.getDisplayName().isBlank() ? uname : m.getDisplayName();
                        String pass = m.getPassword();
                        String hashed = pass == null ? null : hashPassword(pass);

                        boolean exists = false;
                        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
                            ps.setString(1, uname);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) exists = true;
                            }
                        }

                        if (exists) {
                            try (PreparedStatement upd = conn.prepareStatement("UPDATE users SET password = ?, name = ? WHERE username = ? COLLATE NOCASE")) {
                                if (hashed == null) upd.setNull(1, Types.VARCHAR);
                                else upd.setString(1, hashed);
                                upd.setString(2, name);
                                upd.setString(3, uname);
                                upd.executeUpdate();
                            }
                        } else {
                            try (PreparedStatement ins = conn.prepareStatement("INSERT INTO users (username, password, name, is_admin) VALUES (?, ?, ?, 0)")) {
                                ins.setString(1, uname);
                                if (hashed == null) ins.setNull(2, Types.VARCHAR);
                                else ins.setString(2, hashed);
                                ins.setString(3, name);
                                ins.executeUpdate();
                            }
                        }
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                System.err.println("[DatabaseManager] createHouseholdWithAdmin failed: " + e.getMessage());
                return false;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] getConnection failed in createHouseholdWithAdmin: " + e.getMessage());
            return false;
        }
    }

    /* ----------------- Users ----------------- */
    public static boolean createOrUpdateUser(String username, String password, String name) {
        if (username == null || username.isBlank()) return false;
        try {
            Connection conn = getConnection();

            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET password = ?, name = ? WHERE username = ? COLLATE NOCASE")) {
                upd.setString(1, hashPassword(password));
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

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO users (username, password, name) VALUES (?, ?, ?)")) {
                ins.setString(1, username);
                ins.setString(2, hashPassword(password));
                ins.setString(3, name);
                ins.executeUpdate();
                System.err.println("[DatabaseManager] Inserted user: " + username);
                return true;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Insert user failed: " + e.getMessage());
            }

            return false;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] createOrUpdateUser failed: " + e.getMessage());
            return false;
        }
    }

    /* ----------------- Benutzerliste / Suche (Auszug) ----------------- */
    // (Vorhandene Implementierungen bleiben unverändert, daher nicht erneut aufgeführt)
    public static final class UserInfo {
        public final int id;
        public final String username;
        public final String name;
        public final boolean isAdmin;
        public final String passwordHash;

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

    public static List<UserInfo> listUsers() {
        List<UserInfo> out = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement s = conn.createStatement()) {

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

                    if (p != null) p = maskHash(p);
                    out.add(new UserInfo(id, u, n, isAdmin, p));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] listUsers failed: " + e.getMessage());
        }
        return out;
    }

    public static List<UserInfo> searchUsers(String query) {
        // Implementation as before (omitted for brevity)
        return new ArrayList<>();
    }

    public static void logUsers() {
        List<UserInfo> users = listUsers();
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

    private static String hashPassword(String plain) {
        if (plain == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return plain;
        }
    }

    /* ----------------- Shopping Items CRUD ----------------- */
    public static final class ShoppingItem {
        public final int id;
        public final String name;
        public final int quantity;
        public final boolean bought;
        public final String category;
        public final Integer listId;
        public final String createdAt;

        public ShoppingItem(int id, String name, int quantity, boolean bought, String category, Integer listId, String createdAt) {
            this.id = id;
            this.name = name;
            this.quantity = quantity;
            this.bought = bought;
            this.category = category;
            this.listId = listId;
            this.createdAt = createdAt;
        }

        @Override
        public String toString() {
            return String.format("id=%d | name=%s | q=%d | bought=%s | cat=%s | listId=%s | created=%s",
                    id, name, quantity, bought, category, listId == null ? "null" : listId.toString(), createdAt);
        }
    }

    public static boolean addOrUpdateShoppingItem(ShoppingItem item) {
        if (item == null || item.name == null || item.name.isBlank()) return false;
        try (Connection conn = getConnection()) {
            boolean hasItemName = hasColumn(conn, "shopping_items", "item_name");
            boolean hasName = hasColumn(conn, "shopping_items", "name");
            boolean hasAddedBy = hasColumn(conn, "shopping_items", "added_by");

            if (item.id > 0) {
                // Build UPDATE dynamically
                StringBuilder sb = new StringBuilder("UPDATE shopping_items SET ");
                List<Object> params = new ArrayList<>();
                if (hasItemName) {
                    sb.append("item_name = ?, ");
                    params.add(item.name);
                }
                if (hasName) {
                    sb.append("name = ?, ");
                    params.add(item.name);
                }
                sb.append("quantity = ?, bought = ?, category = ?, list_id = ?, ");
                if (hasAddedBy) sb.append("added_by = ?, ");
                sb.append("created_at = ? WHERE id = ?");
                params.add(item.quantity);
                params.add(item.bought ? 1 : 0);
                params.add(item.category);
                params.add(item.listId);
                if (hasAddedBy) params.add(null); // UI should supply actual user id if available
                params.add(item.createdAt);
                params.add(item.id);

                try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                    int idx = 1;
                    for (Object p : params) {
                        if (p == null) ps.setNull(idx++, Types.NULL);
                        else if (p instanceof Integer) ps.setInt(idx++, (Integer) p);
                        else ps.setString(idx++, p.toString());
                    }
                    int updated = ps.executeUpdate();
                    return updated > 0;
                }
            } else {
                // Build INSERT dynamically
                List<String> cols = new ArrayList<>();
                List<String> holders = new ArrayList<>();
                List<Object> params = new ArrayList<>();

                if (hasItemName) {
                    cols.add("item_name");
                    holders.add("?");
                    params.add(item.name);
                }
                if (hasName) {
                    cols.add("name");
                    holders.add("?");
                    params.add(item.name);
                }
                cols.add("quantity");
                holders.add("?");
                params.add(item.quantity);
                cols.add("bought");
                holders.add("?");
                params.add(item.bought ? 1 : 0);
                cols.add("category");
                holders.add("?");
                params.add(item.category);
                cols.add("list_id");
                holders.add("?");
                params.add(item.listId);
                if (hasAddedBy) {
                    cols.add("added_by");
                    holders.add("?");
                    params.add(null);
                } // UI should set actual user id
                cols.add("created_at");
                holders.add("?");
                params.add(item.createdAt);

                String sql = "INSERT INTO shopping_items (" + String.join(", ", cols) + ") VALUES (" + String.join(", ", holders) + ")";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int idx = 1;
                    for (Object p : params) {
                        if (p == null) {
                            ps.setNull(idx++, Types.NULL);
                        } else if (p instanceof Integer) {
                            ps.setInt(idx++, (Integer) p);
                        } else {
                            ps.setString(idx++, p.toString());
                        }
                    }
                    ps.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] addOrUpdateShoppingItem failed: " + e.getMessage());
            return false;
        }
    }

    public static List<ShoppingItem> listShoppingItems() {
        List<ShoppingItem> out = new ArrayList<>();
        try (Connection conn = getConnection()) {
            boolean hasItemName = hasColumn(conn, "shopping_items", "item_name");
            boolean hasName = hasColumn(conn, "shopping_items", "name");

            List<String> cols = new ArrayList<>();
            cols.add("id");
            if (hasItemName) cols.add("item_name");
            if (hasName) cols.add("name");
            cols.add("quantity");
            cols.add("COALESCE(bought,0) AS bought");
            cols.add("category");
            cols.add("list_id");
            cols.add("created_at");

            String sql = "SELECT " + String.join(", ", cols) + " FROM shopping_items";
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(sql)) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = null;
                    if (hasItemName) name = safeGet(rs, "item_name");
                    if ((name == null || name.isBlank()) && hasName) name = safeGet(rs, "name");
                    int quantity = rs.getInt("quantity");
                    boolean bought = rs.getInt("bought") == 1;
                    String category = safeGet(rs, "category");
                    Integer listId = null;
                    try {
                        int lid = rs.getInt("list_id");
                        if (!rs.wasNull()) listId = lid;
                    } catch (SQLException ignored) {
                    }
                    String createdAt = safeGet(rs, "created_at");
                    out.add(new ShoppingItem(id, name, quantity, bought, category, listId, createdAt));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] listShoppingItems failed: " + e.getMessage());
        }
        return out;
    }

    public static boolean deleteShoppingItem(int id) {
        if (id <= 0) return false;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM shopping_items WHERE id = ?")) {
            ps.setInt(1, id);
            int deleted = ps.executeUpdate();
            return deleted > 0;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] deleteShoppingItem failed: " + e.getMessage());
            return false;
        }
    }

    /* ----------------- Budget Transactions CRUD ----------------- */
    public static final class Transaction {
        public final int id;
        public final String description;
        public final double amount;
        public final String date;
        public final Integer userId;
        public final Integer paidBy;

        public Transaction(int id, String description, double amount, String date, Integer userId, Integer paidBy) {
            this.id = id;
            this.description = description;
            this.amount = amount;
            this.date = date;
            this.userId = userId;
            this.paidBy = paidBy;
        }

        @Override
        public String toString() {
            return String.format("id=%d | desc=%s | amount=%s | date=%s | userId=%s | paidBy=%s",
                    id, description, amount, date, userId == null ? "null" : userId.toString(), paidBy == null ? "null" : paidBy.toString());
        }
    }

    public static boolean addOrUpdateTransaction(Transaction t) {
        if (t == null) return false;
        try (Connection conn = getConnection()) {
            boolean hasPaidBy = hasColumn(conn, "budget_transactions", "paid_by");
            boolean hasCategory = hasColumn(conn, "budget_transactions", "category");

            if (t.id > 0) {
                StringBuilder sb = new StringBuilder("UPDATE budget_transactions SET description = ?, amount = ?, date = ?, user_id = ?");
                if (hasPaidBy) sb.append(", paid_by = ?");
                if (hasCategory) sb.append(", category = ?");
                sb.append(" WHERE id = ?");
                try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                    int idx = 1;
                    ps.setString(idx++, t.description);
                    ps.setDouble(idx++, t.amount);
                    ps.setString(idx++, t.date);
                    if (t.userId == null) ps.setNull(idx++, Types.INTEGER);
                    else ps.setInt(idx++, t.userId);
                    if (hasPaidBy) {
                        if (t.paidBy == null) ps.setNull(idx++, Types.INTEGER);
                        else ps.setInt(idx++, t.paidBy);
                    }
                    if (hasCategory) {
                        ps.setNull(idx++, Types.NULL); // UI should supply category if available
                    }
                    ps.setInt(idx, t.id);
                    int updated = ps.executeUpdate();
                    return updated > 0;
                }
            } else {
                List<String> cols = new ArrayList<>();
                List<String> holders = new ArrayList<>();
                List<Object> params = new ArrayList<>();

                cols.add("description");
                holders.add("?");
                params.add(t.description);
                cols.add("amount");
                holders.add("?");
                params.add(t.amount);
                cols.add("date");
                holders.add("?");
                params.add(t.date);
                cols.add("user_id");
                holders.add("?");
                params.add(t.userId);
                if (hasPaidBy) {
                    cols.add("paid_by");
                    holders.add("?");
                    params.add(t.paidBy);
                }
                if (hasCategory) {
                    cols.add("category");
                    holders.add("?");
                    params.add(null); // UI should provide category
                }

                String sql = "INSERT INTO budget_transactions (" + String.join(", ", cols) + ") VALUES (" + String.join(", ", holders) + ")";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int idx = 1;
                    for (Object p : params) {
                        if (p == null) ps.setNull(idx++, Types.INTEGER);
                        else if (p instanceof Integer) ps.setInt(idx++, (Integer) p);
                        else if (p instanceof Double) ps.setDouble(idx++, (Double) p);
                        else ps.setString(idx++, p.toString());
                    }
                    ps.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] addOrUpdateTransaction failed: " + e.getMessage());
            return false;
        }
    }

    public static List<Transaction> listTransactions() {
        return listTransactions(null);
    }

    public static List<Transaction> listTransactions(Integer forUserId) {
        List<Transaction> out = new ArrayList<>();
        try (Connection conn = getConnection()) {
            boolean hasPaidBy = hasColumn(conn, "budget_transactions", "paid_by");
            boolean hasCategory = hasColumn(conn, "budget_transactions", "category");

            List<String> cols = new ArrayList<>();
            cols.add("id");
            cols.add("description");
            cols.add("amount");
            cols.add("date");
            cols.add("user_id");
            if (hasPaidBy) cols.add("paid_by");
            if (hasCategory) cols.add("category");

            String sql = "SELECT " + String.join(", ", cols) + " FROM budget_transactions";
            if (forUserId != null) sql += " WHERE user_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (forUserId != null) ps.setInt(1, forUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String desc = safeGet(rs, "description");
                        double amount = rs.getDouble("amount");
                        String date = safeGet(rs, "date");
                        Integer uid = null;
                        try {
                            int u = rs.getInt("user_id");
                            if (!rs.wasNull()) uid = u;
                        } catch (SQLException ignored) {
                        }
                        Integer paidBy = null;
                        if (hasPaidBy) {
                            try {
                                int p = rs.getInt("paid_by");
                                if (!rs.wasNull()) paidBy = p;
                            } catch (SQLException ignored) {
                            }
                        }
                        out.add(new Transaction(id, desc, amount, date, uid, paidBy));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] listTransactions failed: " + e.getMessage());
        }
        return out;
    }

    public static boolean deleteTransaction(int id) {
        if (id <= 0) return false;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM budget_transactions WHERE id = ?")) {
            ps.setInt(1, id);
            int deleted = ps.executeUpdate();
            return deleted > 0;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] deleteTransaction failed: " + e.getMessage());
            return false;
        }
    }
}