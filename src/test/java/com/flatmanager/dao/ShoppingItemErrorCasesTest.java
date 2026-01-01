package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.ShoppingItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShoppingItemErrorCasesTest {

    private final ShoppingItemDao dao = new ShoppingItemDao();
    private static final String DB_FILE = "target/shopping_errorcases.db";

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
    void testInsertWithNullItemNameDoesNotThrowAndPersistsNullName() throws Exception {
        ShoppingItem it = new ShoppingItem(0, null, "1", "u", null, null, false);
        // Insert should not throw for null item_name according to current schema/DAO behavior
        dao.insert(it);
        assertTrue(it.getId() > 0, "insert should set id even when item_name is null");

        // listAll will use COALESCE(item_name, name) so if both are null, the returned itemName may be null
        List<ShoppingItem> all = dao.listAll();
        ShoppingItem loaded = all.stream().filter(x -> x.getId() == it.getId()).findFirst().orElse(null);
        assertNotNull(loaded, "Inserted row should be loadable");
        assertNull(loaded.getItemName(), "itemName should be null when inserted as null and no fallback name was provided");
    }

    @Test
    void testUpdateNonExistingIdDoesNotCreateButNoError() throws Exception {
        // Ensure DB empty
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = conn.prepareStatement("DELETE FROM shopping_items")) {
            ps.execute();
        }
        ShoppingItem ghost = new ShoppingItem(999999, "Nope", "1", "x", "x", null, true);
        // update should not throw and should not create a row
        dao.update(ghost);
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM shopping_items")) {
            var rs = ps.executeQuery();
            if (rs.next()) assertEquals(0, rs.getInt("c"));
        }
    }

    @Test
    void testDeleteBoughtThrowsWhenTableDropped() throws Exception {
        ShoppingItem it = new ShoppingItem(0, "X", "1", "u", null, null, true);
        dao.insert(it);

        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = conn.prepareStatement("DROP TABLE shopping_items")) {
            ps.execute();
        }

        assertThrows(SQLException.class, () -> dao.deleteBought());
    }

    @Test
    void testInitThrowsOnInvalidJdbcUrl() {
        System.setProperty("db.url", "jdbc:invalid:url");
        DatabaseManager.closeConnection();
        assertThrows(SQLException.class, () -> dao.init());
        System.setProperty("db.url", "jdbc:sqlite:" + new File(DB_FILE).getAbsolutePath());
    }
}
