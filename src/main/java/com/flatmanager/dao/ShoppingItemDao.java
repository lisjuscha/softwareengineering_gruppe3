package com.flatmanager.dao;

import com.flatmanager.model.ShoppingItem;
import com.flatmanager.storage.Database;

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
            st.execute("CREATE TABLE IF NOT EXISTS shopping_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "quantity INTEGER DEFAULT 1, " +
                    "note TEXT, " +
                    "bought INTEGER DEFAULT 0)");
        }
    }

    public List<ShoppingItem> listAll() throws SQLException {
        List<ShoppingItem> list = new ArrayList<>();
        String sql = "SELECT id, name, quantity, note, bought FROM shopping_items ORDER BY bought ASC, name ASC";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                int quantity = rs.getInt("quantity");
                String note = rs.getString("note");
                boolean bought = rs.getInt("bought") != 0;

                // Versuche 5-Arg-Konstruktor: (int, String, int, String, boolean)
                ShoppingItem it = createByConstructor(id, name, quantity, note, bought);
                if (it == null) {
                    // Fallback: no-arg + Reflection-Setzen der Felder
                    it = createByReflection(id, name, quantity, note, bought);
                }
                list.add(it);
            }
        }
        return list;
    }

    public void insert(ShoppingItem item) throws SQLException {
        String sql = "INSERT INTO shopping_items (name, quantity, note, bought) VALUES (?, ?, ?, ?)";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, getStringProp(item, "name"));
            ps.setInt(2, getIntProp(item, "quantity", 1));
            ps.setString(3, getStringProp(item, "note"));
            ps.setInt(4, getBooleanProp(item, "bought") ? 1 : 0);
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
        String sql = "UPDATE shopping_items SET name = ?, quantity = ?, note = ?, bought = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, getStringProp(item, "name"));
            ps.setInt(2, getIntProp(item, "quantity", 1));
            ps.setString(3, getStringProp(item, "note"));
            ps.setInt(4, getBooleanProp(item, "bought") ? 1 : 0);
            ps.setInt(5, getIntProp(item, "id", 0));
            ps.executeUpdate();
        }
    }

    public void deleteBought() throws SQLException {
        String sql = "DELETE FROM shopping_items WHERE bought = 1";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    // --- Hilfsfunktionen (Reflection) ---

    private ShoppingItem createByConstructor(int id, String name, int quantity, String note, boolean bought) {
        try {
            Constructor<ShoppingItem> c = ShoppingItem.class.getConstructor(int.class, String.class, int.class, String.class, boolean.class);
            return c.newInstance(id, name, quantity, note, bought);
        } catch (Exception e) {
            return null;
        }
    }

    private ShoppingItem createByReflection(int id, String name, int quantity, String note, boolean bought) {
        try {
            ShoppingItem it = ShoppingItem.class.getDeclaredConstructor().newInstance();
            setFieldIfExists(it, "id", id);
            setFieldIfExists(it, "name", name);
            setFieldIfExists(it, "quantity", quantity);
            setFieldIfExists(it, "note", note);
            setFieldIfExists(it, "bought", bought);
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
                f.set(obj, value);
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
}