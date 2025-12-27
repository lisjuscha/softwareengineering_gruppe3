package com.flatmanager.dao;

import com.flatmanager.dao.ShoppingItemDao;
import com.flatmanager.model.ShoppingItem;
import com.flatmanager.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShoppingItemDaoTest {

    private final ShoppingItemDao dao = new ShoppingItemDao();

    @BeforeEach
    void before() throws Exception {
        // Use a shared in-memory SQLite DB so multiple connections share the same data/schema
        System.setProperty("db.url", "jdbc:sqlite:file:memdb1?mode=memory&cache=shared");
        DatabaseManager.closeConnection();
        // Create a table schema compatible with ShoppingItemDao (ensure both bought and purchased columns plus purchased_for)
        try (Connection c = DatabaseManager.getConnection();
             Statement s = c.createStatement()) {
            // drop any existing table (ensure a clean start)
            s.executeUpdate("DROP TABLE IF EXISTS shopping_items");
            s.executeUpdate("CREATE TABLE shopping_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "item_name TEXT, " +
                    "name TEXT, " +
                    "quantity TEXT DEFAULT '1', " +         // string quantity to allow empty string entries
                    "bought INTEGER DEFAULT 0, " +          // DAO may reference 'bought'
                    "purchased INTEGER DEFAULT 0, " +       // keep 'purchased' for compatibility
                    "category TEXT, " +
                    "added_by TEXT, " +
                    "purchased_for TEXT)");
        }

        // Ensure the DAO performs any required schema adjustments (adds missing optional columns)
        dao.init();
    }

    @AfterEach
    void after() {
        DatabaseManager.closeConnection();
    }

    @Test
    void testInitIsIdempotentAndTableExists() throws Exception {
        // ensure calling init multiple times does not throw and table still exists
        dao.init();
        dao.init();

        try (Connection conn = DatabaseManager.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='shopping_items'")) {
            assertTrue(rs.next(), "Tabelle shopping_items sollte existieren");
        }
    }

    @Test
    void testDeleteBoughtDoesNotThrowWhenNone() throws Exception {
        // no items present, should not throw and count remains 0
        dao.deleteBought();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM shopping_items");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("c"));
        }
    }

    @Test
    void testInsertAndListAll() throws Exception {
        ShoppingItem item = new ShoppingItem(0, "Milk", "1", "u1", "Dairy", "u1", false);
        dao.insert(item);

        List<ShoppingItem> list = dao.listAll();
        assertFalse(list.isEmpty());
        ShoppingItem first = list.get(0);
        assertEquals("Milk", first.getItemName());
        assertEquals("1", first.getQuantity());
    }

    @Test
    void testUpdateAndDeleteBought() throws Exception {
        ShoppingItem item = new ShoppingItem(0, "Bread", "2", "u2", "Bakery", null, false);
        dao.insert(item);

        List<ShoppingItem> list = dao.listAll();
        assertEquals(1, list.size());
        ShoppingItem it = list.get(0);
        // retrieve actual id from DB using a single shared connection for the test
        final int[] rowId = new int[]{-1};
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM shopping_items WHERE COALESCE(item_name, name) = ?")) {
            ps.setString(1, it.getItemName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) rowId[0] = rs.getInt("id");
            }
        }
        assertTrue(rowId[0] > 0, "inserted row id should be available");
        ShoppingItem toUpdate = new ShoppingItem(rowId[0], it.getItemName(), it.getQuantity(), it.getAddedBy(), it.getCategory(), it.getPurchasedFor(), true);
        dao.update(toUpdate);

        // Poll via DAO to avoid low-level connection visibility issues
        boolean seen = false;
        for (int i = 0; i < 20; i++) {
            List<ShoppingItem> after = dao.listAll();
            ShoppingItem updated = after.stream().filter(x -> x.getId() == rowId[0]).findFirst().orElse(null);
            if (updated != null && updated.isPurchased()) {
                seen = true;
                break;
            }
            Thread.sleep(50);
        }
        assertTrue(seen, "DAO: purchased flag should become true after update (timed out)");

        dao.deleteBought();
        try (Connection conn2 = DatabaseManager.getConnection();
             PreparedStatement ps = conn2.prepareStatement("SELECT COUNT(*) AS c FROM shopping_items");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("c"), "DB: all bought items should be deleted");
        }
    }

    @Test
    void testInsertMultipleAndOrder() throws Exception {
        dao.insert(new ShoppingItem(0, "A", "1", "u", "Misc", null, false));
        dao.insert(new ShoppingItem(0, "B", "1", "u", "Misc", null, false));

        List<ShoppingItem> list = dao.listAll();
        assertEquals(2, list.size());
        assertEquals("A", list.get(0).getItemName());
        assertEquals("B", list.get(1).getItemName());
    }

    @Test
    void testUpdateByNameFallback() throws Exception {
        // insert with a name, then update using id=0 and same name (fallback path)
        dao.insert(new ShoppingItem(0, "Cucumber", "1", "u", "Veg", null, false));
        // change purchased flag via update fallback
        ShoppingItem upd = new ShoppingItem(0, "Cucumber", "1", "u", "Veg", null, true);
        dao.update(upd);

        List<ShoppingItem> list = dao.listAll();
        ShoppingItem found = list.stream().filter(x -> "Cucumber".equals(x.getItemName())).findFirst().orElse(null);
        assertNotNull(found);
        assertTrue(found.isPurchased(), "fallback update by name should mark purchased");
    }

    @Test
    void testInsertPurchasedFlagPersists() throws Exception {
        dao.insert(new ShoppingItem(0, "Yogurt", "2", "u", "Dairy", null, true));
        List<ShoppingItem> list = dao.listAll();
        ShoppingItem f = list.stream().filter(x -> "Yogurt".equals(x.getItemName())).findFirst().orElse(null);
        assertNotNull(f);
        assertTrue(f.isPurchased());
    }

    // ----- zusätzliche Tests für höhere Abdeckung -----

    @Test
    void testInsertWithNullFieldsUsesDefaultsOnLoad() throws Exception {
        // quantity and addedBy set to null on the model
        ShoppingItem item = new ShoppingItem(0, "Eggs", null, null, null, null, false);
        dao.insert(item);

        List<ShoppingItem> all = dao.listAll();
        ShoppingItem loaded = all.stream().filter(x -> "Eggs".equals(x.getItemName())).findFirst().orElse(null);
        assertNotNull(loaded);
        // when DB value is NULL, listAll treats quantity==null as "1"
        assertEquals("1", loaded.getQuantity(), "NULL quantity sollte beim Laden als \"1\" interpretiert werden");
        // createByConstructor sets addedBy to empty string when null
        assertEquals("", loaded.getAddedBy(), "NULL added_by sollte als leerer String geladen werden");
    }

    @Test
    void testDirectInsertWithEmptyQuantityRemainsEmptyString() throws Exception {
        // insert via DAO using an explicit empty string for quantity
        dao.insert(new ShoppingItem(0, "EmptyQty", "", null, null, null, false));

        List<ShoppingItem> all = dao.listAll();
        ShoppingItem loaded = all.stream().filter(x -> "EmptyQty".equals(x.getItemName())).findFirst().orElse(null);
        assertNotNull(loaded, "Eintrag mit leerer quantity sollte vorhanden sein");
        // empty string is not null -> listAll returns the empty string
        assertEquals("", loaded.getQuantity(), "Leere quantity-Zeichenkette bleibt leerer String");
    }

    @Test
    void testUpdateNonExistingIdDoesNotCreate() throws Exception {
        // Update mit nicht existierender ID darf keine neue Zeile erzeugen
        ShoppingItem ghost = new ShoppingItem(999999, "Nope", "1", "x", "x", null, true);
        dao.update(ghost);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM shopping_items");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            // DB war leer zu Beginn dieses Tests, bleibt leer
            assertEquals(0, rs.getInt("c"));
        }
    }

    @Test
    void testInitAddsMissingOptionalColumns() throws Exception {
        // use a file-based DB to test column addition by init()
        File db = new File("target/shopping_addcol_test.db");
        if (db.exists()) db.delete();
        try {
            System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
            DatabaseManager.closeConnection();
            // create minimal table without optional columns
            try (Connection conn = DatabaseManager.getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS shopping_items");
                st.executeUpdate("CREATE TABLE shopping_items (id INTEGER PRIMARY KEY AUTOINCREMENT, item_name TEXT, name TEXT)");
            }

            ShoppingItemDao localDao = new ShoppingItemDao();
            // should add purchased, bought, purchased_for columns if missing
            localDao.init();

            try (Connection conn = DatabaseManager.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA table_info(shopping_items)")) {
                boolean hasPurchased = false, hasBought = false, hasPurchasedFor = false;
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("purchased".equalsIgnoreCase(col)) hasPurchased = true;
                    if ("bought".equalsIgnoreCase(col)) hasBought = true;
                    if ("purchased_for".equalsIgnoreCase(col)) hasPurchasedFor = true;
                }
                assertTrue(hasPurchased, "purchased column sollte hinzugefügt worden sein");
                assertTrue(hasBought || hasPurchased, "bought oder purchased column sollte vorhanden sein");
                assertTrue(hasPurchasedFor, "purchased_for column sollte hinzugefügt worden sein");
            }
        } finally {
            DatabaseManager.closeConnection();
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/shopping_addcol_test.db"));
            } catch (Exception ignore) {
            }
            // restore in-memory db for other tests
            System.setProperty("db.url", "jdbc:sqlite:file:memdb1?mode=memory&cache=shared");
            DatabaseManager.closeConnection();
        }
    }
}