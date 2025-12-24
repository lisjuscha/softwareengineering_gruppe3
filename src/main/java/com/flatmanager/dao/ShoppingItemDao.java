package com.flatmanager.dao;

import com.flatmanager.model.ShoppingItem;
import com.flatmanager.storage.Database;
import javafx.beans.property.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ShoppingItemDao {

    public void init() throws SQLException {
        Connection conn = Database.getConnection();
        try (Statement st = conn.createStatement()) {
            // align with main schema used by DatabaseManager / ShoppingListView
            st.execute("CREATE TABLE IF NOT EXISTS shopping_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "item_name TEXT, " +
                    "name TEXT, " +
                    "quantity INTEGER DEFAULT 1, " +
                    "purchased INTEGER DEFAULT 0, " +
                    "category TEXT, " +
                    "added_by TEXT, " +
                    "purchased_for TEXT)");
        }
        // Ensure newer columns exist in case DatabaseManager created a different base schema
        ensureColumnExists(conn, "shopping_items", "purchased_for", "TEXT", "NULL");
        ensureColumnExists(conn, "shopping_items", "purchased", "INTEGER", "0");
    }

    public List<ShoppingItem> listAll() throws SQLException {
        List<ShoppingItem> list = new ArrayList<>();
        // prefer the columns used by the UI: item_name, quantity, added_by, category, purchased_for, purchased
        String sql = "SELECT id, COALESCE(item_name, name) AS item_name, quantity, added_by, category, purchased_for, COALESCE(purchased, bought, 0) AS purchased FROM shopping_items ORDER BY category, item_name";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String itemName = rs.getString("item_name");
                String quantity = rs.getString("quantity");
                String addedBy = rs.getString("added_by");
                String category = rs.getString("category");
                String purchasedFor = rs.getString("purchased_for");
                boolean purchased = rs.getInt("purchased") != 0;

                // create via constructor matching ShoppingItem(int, String, String, String, String, String, boolean)
                ShoppingItem it = createByConstructor(id, itemName, quantity == null ? "1" : quantity, addedBy == null ? "" : addedBy, category, purchasedFor, purchased);
                if (it == null) {
                    // Fallback: no-arg + Reflection-Setzen der Felder
                    it = createByReflection(id, itemName, quantity == null ? "1" : quantity, addedBy, category, purchasedFor, purchased);
                }
                list.add(it);
            }
        }
        return list;
    }

    public void insert(ShoppingItem item) throws SQLException {
        Connection conn = Database.getConnection();
        // ensure optional columns exist where possible
        ensureColumnExists(conn, "shopping_items", "purchased", "INTEGER", "0");
        ensureColumnExists(conn, "shopping_items", "bought", "INTEGER", "0");
        ensureColumnExists(conn, "shopping_items", "purchased_for", "TEXT", "NULL");

        boolean hasPurchasedFor = columnExists(conn, "shopping_items", "purchased_for");
        boolean hasPurchased = columnExists(conn, "shopping_items", "purchased");
        boolean hasBought = columnExists(conn, "shopping_items", "bought");

        StringBuilder sql = new StringBuilder("INSERT INTO shopping_items (item_name, quantity, added_by, category");
        if (hasPurchasedFor) sql.append(", purchased_for");
        if (hasPurchased) sql.append(", purchased");
        if (hasBought) sql.append(", bought");
        sql.append(") VALUES (?, ?, ?, ?");
        if (hasPurchasedFor) sql.append(", ?");
        if (hasPurchased) sql.append(", ?");
        if (hasBought) sql.append(", ?");
        sql.append(")");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
            int idx = 1;
            ps.setString(idx++, item.getItemName());
            ps.setString(idx++, item.getQuantity());
            ps.setString(idx++, item.getAddedBy());
            ps.setString(idx++, item.getCategory());
            if (hasPurchasedFor) ps.setString(idx++, item.getPurchasedFor());
            if (hasPurchased) ps.setInt(idx++, item.isPurchased() ? 1 : 0);
            if (hasBought) ps.setInt(idx++, item.isPurchased() ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys != null && keys.next()) {
                    setIntPropIfExists(item, "id", keys.getInt(1));
                    return;
                }
            } catch (SQLFeatureNotSupportedException ignored) {
            }

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) setIntPropIfExists(item, "id", rs.getInt(1));
            }
        }
    }

    public void update(ShoppingItem item) throws SQLException {
        Connection conn = Database.getConnection();
        // ensure optional columns exist where possible
        ensureColumnExists(conn, "shopping_items", "purchased", "INTEGER", "0");
        ensureColumnExists(conn, "shopping_items", "bought", "INTEGER", "0");
        ensureColumnExists(conn, "shopping_items", "purchased_for", "TEXT", "NULL");

        boolean hasPurchasedFor = columnExists(conn, "shopping_items", "purchased_for");
        boolean hasPurchased = columnExists(conn, "shopping_items", "purchased");
        boolean hasBought = columnExists(conn, "shopping_items", "bought");

        int id = getIntProp(item, "id", 0);
        if (id > 0) {
            // Always include purchased and bought columns (we ensured they exist above)
            StringBuilder sb = new StringBuilder("UPDATE shopping_items SET item_name = ?, quantity = ?, added_by = ?, category = ?, purchased_for = ?, purchased = ?, bought = ? WHERE id = ?");
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                int idx = 1;
                ps.setString(idx++, getStringProp(item, "itemName") != null ? getStringProp(item, "itemName") : getStringProp(item, "name"));
                ps.setString(idx++, getStringProp(item, "quantity"));
                ps.setString(idx++, getStringProp(item, "addedBy"));
                ps.setString(idx++, getStringProp(item, "category"));
                ps.setString(idx++, getStringProp(item, "purchasedFor"));
                ps.setInt(idx++, getBooleanProp(item, "purchased") ? 1 : (getBooleanProp(item, "bought") ? 1 : 0));
                ps.setInt(idx++, getBooleanProp(item, "bought") ? 1 : 0);
                ps.setInt(idx++, id);
                // debug print
                try {
                    System.err.println("[ShoppingItemDao] UPDATE SQL: " + sb.toString());
                    System.err.println("[ShoppingItemDao] params: itemName=" + getStringProp(item, "itemName") +
                            ", quantity=" + getStringProp(item, "quantity") +
                            ", addedBy=" + getStringProp(item, "addedBy") +
                            ", category=" + getStringProp(item, "category") +
                            ", purchasedFor=" + getStringProp(item, "purchasedFor") +
                            ", purchased=" + (getBooleanProp(item, "purchased") ? 1 : 0) +
                            ", bought=" + (getBooleanProp(item, "bought") ? 1 : 0) +
                            ", id=" + id);
                } catch (Exception ignored) {}
                ps.executeUpdate();
                // additionally ensure the boolean flags persisted correctly (some schemas/driver combos may ignore mixed type bindings)
                try {
                    if (hasPurchased) {
                        try (PreparedStatement ps2 = conn.prepareStatement("UPDATE shopping_items SET purchased = ? WHERE id = ?")) {
                            ps2.setInt(1, getBooleanProp(item, "purchased") ? 1 : (getBooleanProp(item, "bought") ? 1 : 0));
                            ps2.setInt(2, id);
                            ps2.executeUpdate();
                        }
                    }
                } catch (SQLException ignored) {
                }
                try {
                    if (hasBought) {
                        try (PreparedStatement ps3 = conn.prepareStatement("UPDATE shopping_items SET bought = ? WHERE id = ?")) {
                            ps3.setInt(1, getBooleanProp(item, "bought") ? 1 : 0);
                            ps3.setInt(2, id);
                            ps3.executeUpdate();
                        }
                    }
                } catch (SQLException ignored) {
                }
                try {
                    conn.commit();
                } catch (SQLException ignore) {}
                // immediate verification on the same connection
                try (PreparedStatement check = conn.prepareStatement("SELECT COALESCE(purchased, bought, 0) AS p FROM shopping_items WHERE id = ?")) {
                    check.setInt(1, id);
                    try (ResultSet crs = check.executeQuery()) {
                        if (crs.next()) {
                            int pv = crs.getInt("p");
                            System.err.println("[ShoppingItemDao] POST-UPDATE check id=" + id + " -> p=" + pv);
                        } else {
                            System.err.println("[ShoppingItemDao] POST-UPDATE check id=" + id + " -> no row");
                        }
                    }
                } catch (SQLException ignore) {}
             }
         } else {
            // fallback: update by item_name or name
            StringBuilder sb = new StringBuilder("UPDATE shopping_items SET quantity = ?, added_by = ?, category = ?");
            if (hasPurchasedFor) sb.append(", purchased_for = ?");
            if (hasPurchased) sb.append(", purchased = ?");
            if (hasBought) sb.append(", bought = ?");
            sb.append(" WHERE COALESCE(item_name, name) = ?");
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                int idx = 1;
                ps.setString(idx++, getStringProp(item, "quantity"));
                ps.setString(idx++, getStringProp(item, "addedBy"));
                ps.setString(idx++, getStringProp(item, "category"));
                if (hasPurchasedFor) ps.setString(idx++, getStringProp(item, "purchasedFor"));
                if (hasPurchased) ps.setInt(idx++, getBooleanProp(item, "purchased") ? 1 : (getBooleanProp(item, "bought") ? 1 : 0));
                if (hasBought) ps.setInt(idx++, getBooleanProp(item, "bought") ? 1 : 0);
                ps.setString(idx++, getStringProp(item, "itemName") != null ? getStringProp(item, "itemName") : getStringProp(item, "name"));
                ps.executeUpdate();
                try { conn.commit(); } catch (SQLException ignore) {}
            }
        }
    }

    public void deleteBought() throws SQLException {
        String sql = "DELETE FROM shopping_items WHERE COALESCE(purchased, bought, 0) = 1";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    // --- Hilfsfunktionen (Reflection) ---

    private ShoppingItem createByConstructor(int id, String itemName, String quantity, String addedBy, String category, String purchasedFor, boolean purchased) {
        try {
            Constructor<ShoppingItem> c = ShoppingItem.class.getConstructor(int.class, String.class, String.class, String.class, String.class, String.class, boolean.class);
            return c.newInstance(id, itemName, quantity, addedBy, category, purchasedFor, purchased);
        } catch (Exception e) {
            return null;
        }
    }

    private ShoppingItem createByReflection(int id, String itemName, String quantity, String addedBy, String category, String purchasedFor, boolean purchased) {
        try {
            ShoppingItem it = ShoppingItem.class.getDeclaredConstructor().newInstance();
            setFieldIfExists(it, "id", id);
            setFieldIfExists(it, "itemName", itemName);
            setFieldIfExists(it, "quantity", quantity);
            setFieldIfExists(it, "addedBy", addedBy);
            setFieldIfExists(it, "category", category);
            setFieldIfExists(it, "purchasedFor", purchasedFor);
            setFieldIfExists(it, "purchased", purchased);
            return it;
        } catch (Exception e) {
            // Falls kein no-arg Konstruktor vorhanden -> propagate as runtime wrapped in SQLException
            throw new RuntimeException("Unable to construct ShoppingItem via reflection", e);
        }
    }

    private String getStringProp(ShoppingItem item, String prop) {
        try {
            // getter e.g. getName()
            Method m = findMethod(item.getClass(), "get" + capitalize(prop));
            if (m != null) {
                Object val = m.invoke(item);
                return val != null ? String.valueOf(val) : null;
            }
            // direct field
            Field f = findField(item.getClass(), prop);
            if (f != null) {
                Object v = f.get(item);
                return v != null ? String.valueOf(v) : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int getIntProp(Object obj, String prop, int defaultValue) {
        try {
            Method m = findMethod(obj.getClass(), "get" + capitalize(prop));
            if (m != null) {
                Object val = m.invoke(obj);
                if (val instanceof Number) return ((Number) val).intValue();
                if (val != null) return Integer.parseInt(String.valueOf(val));
            }
            Field f = findField(obj.getClass(), prop);
            if (f != null) {
                Object v = f.get(obj);
                if (v instanceof Number) return ((Number) v).intValue();
                if (v != null) return Integer.parseInt(String.valueOf(v));
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private boolean getBooleanProp(Object obj, String prop) {
        try {
            Method m = findMethod(obj.getClass(), "is" + capitalize(prop));
            if (m == null) m = findMethod(obj.getClass(), "get" + capitalize(prop));
            if (m != null) {
                Object val = m.invoke(obj);
                if (val instanceof Boolean) return (Boolean) val;
                if (val instanceof Number) return ((Number) val).intValue() != 0;
                if (val != null) return Boolean.parseBoolean(String.valueOf(val));
            }
            Field f = findField(obj.getClass(), prop);
            if (f != null) {
                Object v = f.get(obj);
                if (v instanceof Boolean) return (Boolean) v;
                if (v instanceof Number) return ((Number) v).intValue() != 0;
                if (v != null) return Boolean.parseBoolean(String.valueOf(v));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void setIntPropIfExists(Object obj, String prop, int value) {
        try {
            Method m = findMethod(obj.getClass(), "set" + capitalize(prop), int.class);
            if (m != null) {
                m.invoke(obj, value);
                return;
            }
            Field f = findField(obj.getClass(), prop);
            if (f != null) {
                f.set(obj, value);
            }
        } catch (Exception ignored) {
        }
    }

    private void setFieldIfExists(Object obj, String fieldName, Object value) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f != null) {
                f.setAccessible(true);
                Class<?> ft = f.getType();
                // If the target is a JavaFX Property, update the property's value instead of overwriting the field
                if (Property.class.isAssignableFrom(ft)) {
                    Object propObj = f.get(obj);
                    if (propObj instanceof IntegerProperty) {
                        int v = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
                        ((IntegerProperty) propObj).set(v);
                    } else if (propObj instanceof StringProperty) {
                        ((StringProperty) propObj).set(value == null ? null : String.valueOf(value));
                    } else if (propObj instanceof BooleanProperty) {
                        boolean b = false;
                        if (value instanceof Boolean) b = (Boolean) value;
                        else if (value instanceof Number) b = ((Number) value).intValue() != 0;
                        else if (value != null) b = Boolean.parseBoolean(String.valueOf(value));
                        ((BooleanProperty) propObj).set(b);
                    } else if (propObj instanceof DoubleProperty) {
                        double d = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
                        ((DoubleProperty) propObj).set(d);
                    } else {
                        // fallback: attempt to set via toString
                        try {
                            Method m = obj.getClass().getMethod("set" + capitalize(fieldName), String.class);
                            m.setAccessible(true);
                            m.invoke(obj, value == null ? null : String.valueOf(value));
                        } catch (Exception ignore) {
                            // last resort: overwrite field
                            f.set(obj, value);
                        }
                    }
                } else {
                    f.set(obj, value);
                }
            } else {
                Method m = findMethod(obj.getClass(), "set" + capitalize(fieldName), value != null ? value.getClass() : String.class);
                if (m != null) m.invoke(obj, value);
            }
        } catch (Exception ignored) {
        }
    }

    private Method findMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            // search declared methods (including non-public)
            try {
                Method m = cls.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private Field findField(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // Adds a column to a table if it does not exist already.
    private void ensureColumnExists(Connection conn, String tableName, String columnName, String columnType, String defaultValueSql) {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            boolean found = false;
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equalsIgnoreCase(name)) { found = true; break; }
            }
            if (!found) {
                String alter = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType + (defaultValueSql != null ? " DEFAULT " + defaultValueSql : "");
                try (Statement s2 = conn.createStatement()) {
                    s2.executeUpdate(alter);
                } catch (SQLException ex) {
                    System.err.println("[ShoppingItemDao] Could not add column " + columnName + ": " + ex.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("[ShoppingItemDao] Could not inspect table " + tableName + ": " + e.getMessage());
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equalsIgnoreCase(name)) return true;
            }
        } catch (SQLException e) {
            System.err.println("[ShoppingItemDao] Could not inspect table " + tableName + ": " + e.getMessage());
        }
        return false;
    }
}

