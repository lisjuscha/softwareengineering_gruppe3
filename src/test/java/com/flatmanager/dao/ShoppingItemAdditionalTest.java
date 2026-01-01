package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.ShoppingItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShoppingItemAdditionalTest {

    private final ShoppingItemDao dao = new ShoppingItemDao();
    private static final String DB_FILE = "target/shopping_additional.db";

    @BeforeEach
    void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
        dao.init();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(DB_FILE)); } catch (Exception ignore) {}
    }

    @Test
    void testVeryLongNameAndUnicode() throws Exception {
        String longName = "X".repeat(100_000) + " Üñíçødé 🚀 'quotes' \"double\"";
        ShoppingItem it = new ShoppingItem(0, longName, "1", "u", "Misc", null, false);
        dao.insert(it);

        List<ShoppingItem> all = dao.listAll();
        ShoppingItem loaded = all.stream().filter(x -> x.getId() == it.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(longName.length(), loaded.getItemName().length());
        assertEquals(longName, loaded.getItemName());
    }

    @Test
    void testZeroAndNegativeQuantityStringsArePreserved() throws Exception {
        ShoppingItem a = new ShoppingItem(0, "ZeroQty", "0", "u", null, null, false);
        ShoppingItem b = new ShoppingItem(0, "NegQty", "-1", "u", null, null, false);
        dao.insert(a);
        dao.insert(b);

        List<ShoppingItem> list = dao.listAll();
        ShoppingItem la = list.stream().filter(x -> "ZeroQty".equals(x.getItemName())).findFirst().orElse(null);
        ShoppingItem lb = list.stream().filter(x -> "NegQty".equals(x.getItemName())).findFirst().orElse(null);
        assertNotNull(la);
        assertNotNull(lb);
        assertEquals("0", la.getQuantity());
        assertEquals("-1", lb.getQuantity());
    }

    @Test
    void testSqlInjectionLikeNameIsStoredLiteralAndTableIntact() throws Exception {
        String name = "Robert'); DROP TABLE shopping_items;--";
        ShoppingItem it = new ShoppingItem(0, name, "1", "u", "Misc", null, false);
        dao.insert(it);

        ShoppingItem loaded = dao.listAll().stream().filter(x -> name.equals(x.getItemName())).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(name, loaded.getItemName());

        // table should still exist
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='shopping_items'")) {
            assertTrue(rs.next());
        }
    }

    @Test
    void testMultipleInsertsAssignIncreasingIds() throws Exception {
        ShoppingItem t1 = new ShoppingItem(0, "A", "1", "u", null, null, false);
        ShoppingItem t2 = new ShoppingItem(0, "B", "1", "u", null, null, false);
        dao.insert(t1);
        dao.insert(t2);
        assertTrue(t1.getId() > 0);
        assertTrue(t2.getId() > t1.getId());
    }

    @Test
    void testPurchasedAndBoughtFlagsPersist() throws Exception {
        ShoppingItem it = new ShoppingItem(0, "Milk", "2", "u", "Dairy", null, true);
        dao.insert(it);
        ShoppingItem loaded = dao.listAll().stream().filter(x -> x.getId() == it.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertTrue(loaded.isPurchased());
    }

    @Test
    void testDirectInsertWithEmptyQuantityRemainsEmptyString() throws Exception {
        // insert via DAO using an explicit empty string for quantity
        dao.insert(new ShoppingItem(0, "EmptyQty", "", null, null, null, false));

        List<ShoppingItem> all = dao.listAll();
        ShoppingItem loaded = all.stream().filter(x -> "EmptyQty".equals(x.getItemName())).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals("", loaded.getQuantity());
    }
}

