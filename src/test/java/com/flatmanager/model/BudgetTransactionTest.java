package com.flatmanager.model;

import com.flatmanager.dao.BudgetTransactionDao;
import com.flatmanager.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetTransactionTest {

    // --- Unit-Tests für das Model ---

    @Test
    void testSetDateWithLocalDateConvertsToIsoString() {
        BudgetTransaction t = new BudgetTransaction();
        t.setDate(LocalDate.of(2025, 12, 24));
        assertEquals("2025-12-24", t.getDate());
    }

    @Test
    void testSetDateWithStringKeepsValue() {
        BudgetTransaction t = new BudgetTransaction();
        t.setDate("2024-01-01");
        assertEquals("2024-01-01", t.getDate());
    }

    @Test
    void testSetDateNullForBothOverloadsResultsInNull() {
        BudgetTransaction a = new BudgetTransaction();
        a.setDate((LocalDate) null);
        assertNull(a.getDate());

        BudgetTransaction b = new BudgetTransaction();
        b.setDate((String) null);
        assertNull(b.getDate());
    }

    @Test
    void testAccessorsAndDefaultConstructor() {
        BudgetTransaction t = new BudgetTransaction();
        t.setId(7);
        t.setAmount(42.5);
        t.setDate("2023-03-03");
        t.setDescription("Test desc");
        t.setCategory("Test cat");
        t.setMeta("meta");

        assertEquals(7, t.getId());
        assertEquals(42.5, t.getAmount(), 0.0001);
        assertEquals("2023-03-03", t.getDate());
        assertEquals("Test desc", t.getDescription());
        assertEquals("Test cat", t.getCategory());
        assertEquals("meta", t.getMeta());
    }

    @Test
    void testEqualsAndHashCode_considersAllFields() {
        BudgetTransaction a = new BudgetTransaction(1, 10.0, "2025-01-01", "d", "c", "m");
        BudgetTransaction b = new BudgetTransaction(1, 10.0, "2025-01-01", "d", "c", "m");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        BudgetTransaction different = new BudgetTransaction(2, 10.0, "2025-01-01", "d", "c", "m");
        assertNotEquals(a, different);
    }

    @Test
    void testToStringContainsKeyFields() {
        BudgetTransaction t = new BudgetTransaction(5, 99.5, "2023-03-03", "desc", "cat", "meta");
        String s = t.toString();
        assertTrue(s.contains("id=5") || s.contains("id:5"));
        assertTrue(s.contains("amount=99.5") || s.contains("amount:99.5"));
        assertTrue(s.contains("2023-03-03"));
        assertTrue(s.contains("desc"));
        assertTrue(s.contains("cat"));
        assertTrue(s.contains("meta"));
    }

    // --- Integrationstests gegen eine temporäre SQLite-DB ---

    private static final String DB_PATH = "target/budget_transaction_integ.db";
    private final BudgetTransactionDao dao = new BudgetTransactionDao();

    @BeforeEach
    void beforeEach() throws Exception {
        File db = new File(DB_PATH);
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        // ensure closed from previous runs
        DatabaseManager.closeConnection();
        // initialize DAO (creates tables)
        dao.init();
    }

    @AfterEach
    void afterEach() throws Exception {
        DatabaseManager.closeConnection();
        try {
            Files.deleteIfExists(Path.of(DB_PATH));
        } catch (Exception ignore) {
        }
    }

    @Test
    void integ_daoInitIsIdempotent_and_tableExists() throws Exception {
        // second init should not fail
        dao.init();

        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='budget_transactions'")) {
            assertTrue(rs.next(), "Tabelle budget_transactions sollte existieren");
        }
    }

    @Test
    void integ_insertAndLoadWithLocalDate() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(15.5);
        t.setDate(LocalDate.of(2025, 5, 5));
        t.setDescription("IntegrationTest");
        t.setCategory("TestCat");

        dao.insert(t);
        assertTrue(t.getId() > 0, "insert sollte eine Id setzen");

        List<BudgetTransaction> all = dao.listAll();
        BudgetTransaction loaded = all.stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals("2025-05-05", loaded.getDate());
        assertEquals("IntegrationTest", loaded.getDescription());
        assertEquals("TestCat", loaded.getCategory());
    }

    @Test
    void integ_insertWithNullDatePersistsNull() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(5.0);
        t.setDate((LocalDate) null);
        t.setDescription("NoDate");
        dao.insert(t);
        assertTrue(t.getId() > 0);

        List<BudgetTransaction> all = dao.listAll();
        BudgetTransaction loaded = all.stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertNull(loaded.getDate(), "Datum muss null sein wenn null eingefügt wurde");
    }

    @Test
    void integ_updateAndDeleteLifecycle() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(12.5);
        t.setDate("2025-12-24");
        t.setDescription("Pizza");
        t.setCategory("Essen");

        dao.insert(t);
        assertTrue(t.getId() > 0);

        // update
        t.setAmount(13.0);
        t.setDescription("Pizza (hot)");
        dao.update(t);

        BudgetTransaction up = dao.listAll().stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(up);
        assertEquals(13.0, up.getAmount(), 0.0001);
        assertEquals("Pizza (hot)", up.getDescription());

        // delete
        dao.deleteById(t.getId());
        List<BudgetTransaction> after = dao.listAll();
        assertTrue(after.stream().noneMatch(x -> x.getId() == t.getId()));
    }
}