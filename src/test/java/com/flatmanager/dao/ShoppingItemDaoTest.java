package com.flatmanager.dao;

import com.flatmanager.dao.ShoppingItemDao;
import com.flatmanager.model.ShoppingItem;
import com.flatmanager.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShoppingItemDaoTest {

    private final ShoppingItemDao dao = new ShoppingItemDao();

    @BeforeEach
    void before() throws Exception {
        // Use a shared in-memory SQLite DB so multiple connections share the same data/schema
        System.setProperty("db.url", "jdbc:sqlite:file:memdb1?mode=memory&cache=shared");
        DatabaseManager.closeConnection();
        // Create a table schema compatible with ShoppingItemDao (name, quantity, note, bought)
        // Use the DatabaseManager connection and keep it open for DAO usage
        java.sql.Connection c = DatabaseManager.getConnection();
        try (java.sql.Statement s = c.createStatement()) {
            // drop any existing table (ensure a clean start)
            s.executeUpdate("DROP TABLE IF EXISTS shopping_items");
            s.executeUpdate("CREATE TABLE shopping_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "item_name TEXT, " +
                    "name TEXT, " +
                    "quantity INTEGER DEFAULT 1, " +
                    "purchased INTEGER DEFAULT 0, " +
                    "category TEXT, " +
                    "added_by TEXT, " +
                    "purchased_for TEXT)");
        }
    }

    @AfterEach
    void after() {
        DatabaseManager.closeConnection();
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
            if (updated != null && updated.isPurchased()) { seen = true; break; }
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
}
