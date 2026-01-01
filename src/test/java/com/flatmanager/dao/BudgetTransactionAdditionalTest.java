package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.BudgetTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetTransactionAdditionalTest {

    private final BudgetTransactionDao dao = new BudgetTransactionDao();
    private static final String DB_FILE = "target/budget_additional.db";

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
    void testInsertNegativeAmountAndPrecision() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(-1234.56);
        t.setDate(LocalDate.of(2025, 6, 15));
        t.setDescription("NegativeAmount");
        dao.insert(t);
        assertTrue(t.getId() > 0);

        List<BudgetTransaction> all = dao.listAll();
        BudgetTransaction loaded = all.stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(-1234.56, loaded.getAmount(), 0.0001);
        assertEquals("NegativeAmount", loaded.getDescription());
    }

    @Test
    void testOrderByDateDescForNonNullDates() throws Exception {
        BudgetTransaction a = new BudgetTransaction();
        a.setAmount(1.0);
        a.setDate(LocalDate.of(2025, 5, 5));
        a.setDescription("A");
        dao.insert(a);

        BudgetTransaction b = new BudgetTransaction();
        b.setAmount(2.0);
        b.setDate(LocalDate.of(2024, 5, 5));
        b.setDescription("B");
        dao.insert(b);

        List<BudgetTransaction> all = dao.listAll();
        assertTrue(all.size() >= 2);
        // first element should be the one with the later date (2025-05-05)
        BudgetTransaction first = all.get(0);
        assertEquals("A", first.getDescription());
    }

    @Test
    void testInsertNullCategoryAndDescription() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(5.0);
        t.setDate(LocalDate.of(2023, 1, 1));
        t.setDescription(null);
        t.setCategory(null);
        dao.insert(t);
        assertTrue(t.getId() > 0);

        BudgetTransaction loaded = dao.listAll().stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertNull(loaded.getDescription());
        assertNull(loaded.getCategory());
    }

    @Test
    void testDescriptionWithQuotesAndSpecialChars() throws Exception {
        String desc = "O'Reilly \"quote\" \n newline";
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(7.7);
        t.setDate(LocalDate.of(2022, 2, 2));
        t.setDescription(desc);
        dao.insert(t);

        BudgetTransaction loaded = dao.listAll().stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(desc, loaded.getDescription());
    }

    @Test
    void testUpdateNonExistingDoesNotThrowAndDoesNotCreate() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setId(99999); // non-existing
        t.setAmount(1.23);
        t.setDate(LocalDate.of(2020,1,1));
        t.setDescription("Ghost");

        // should not throw
        dao.update(t);

        List<BudgetTransaction> all = dao.listAll();
        assertTrue(all.stream().noneMatch(x -> x.getId() == 99999));
    }

    @Test
    void testMultipleInsertsAssignIncreasingIds() throws Exception {
        BudgetTransaction t1 = new BudgetTransaction();
        t1.setAmount(1);
        t1.setDate(LocalDate.of(2023,3,3));
        dao.insert(t1);

        BudgetTransaction t2 = new BudgetTransaction();
        t2.setAmount(2);
        t2.setDate(LocalDate.of(2023,3,4));
        dao.insert(t2);

        assertTrue(t1.getId() > 0);
        assertTrue(t2.getId() > t1.getId());
    }

    @Test
    void testLeapDayDateInsert() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(9.99);
        t.setDate(LocalDate.of(2024, 2, 29)); // Schaltjahr
        t.setDescription("LeapDay");
        dao.insert(t);
        BudgetTransaction loaded = dao.listAll().stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals("2024-02-29", loaded.getDate());
    }

    @Test
    void testVeryLongDescription() throws Exception {
        String longDesc = "a".repeat(100000); // 100k chars
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(0.01);
        t.setDate(LocalDate.of(2025, 7, 7));
        t.setDescription(longDesc);
        dao.insert(t);
        BudgetTransaction loaded = dao.listAll().stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(longDesc.length(), loaded.getDescription().length());
        assertEquals(longDesc, loaded.getDescription());
    }

    @Test
    void testZeroAndNegativeZeroAmounts() throws Exception {
        BudgetTransaction t1 = new BudgetTransaction();
        t1.setAmount(0.0);
        t1.setDate(LocalDate.of(2023, 8, 8));
        t1.setDescription("Zero");
        dao.insert(t1);

        BudgetTransaction t2 = new BudgetTransaction();
        t2.setAmount(-0.0);
        t2.setDate(LocalDate.of(2023, 8, 9));
        t2.setDescription("NegZero");
        dao.insert(t2);

        BudgetTransaction l1 = dao.listAll().stream().filter(x -> x.getId() == t1.getId()).findFirst().orElse(null);
        BudgetTransaction l2 = dao.listAll().stream().filter(x -> x.getId() == t2.getId()).findFirst().orElse(null);
        assertNotNull(l1);
        assertNotNull(l2);
        assertEquals(0.0, l1.getAmount());
        assertEquals(0.0, l2.getAmount());
    }

    @Test
    void testMaxDoubleAmount() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(Double.MAX_VALUE);
        t.setDate(LocalDate.of(2025, 9, 9));
        t.setDescription("MaxDouble");
        dao.insert(t);
        BudgetTransaction loaded = dao.listAll().stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(Double.MAX_VALUE, loaded.getAmount(), 0.0);
    }

    @Test
    void testSqlInjectionLikeDescriptionIsStoredLiteral() throws Exception {
        String desc = "Robert'); DROP TABLE budget_transactions;--";
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(11.11);
        t.setDate(LocalDate.of(2021, 1, 1));
        t.setDescription(desc);
        dao.insert(t);
        BudgetTransaction loaded = dao.listAll().stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertEquals(desc, loaded.getDescription());

        // verify table still exists
        try (java.sql.Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='budget_transactions'")) {
            assertTrue(rs.next());
        }
    }

    @Test
    void testUpdateSetDateToNull() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(4.44);
        t.setDate(LocalDate.of(2022, 3, 3));
        t.setDescription("ToNull");
        dao.insert(t);
        assertTrue(t.getId() > 0);

        t.setDate((LocalDate) null);
        dao.update(t);

        BudgetTransaction loaded = dao.listAll().stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(loaded);
        assertNull(loaded.getDate(), "Datum sollte nach Update auf null wirklich null sein");
    }
}
