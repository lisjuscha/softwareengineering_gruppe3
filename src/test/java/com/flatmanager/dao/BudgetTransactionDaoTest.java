package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.BudgetTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetTransactionDaoTest {

    private final BudgetTransactionDao dao = new BudgetTransactionDao();

    @BeforeEach
    void before() throws Exception {
        File db = new File("target/budget_test.db");
        if (db.exists()) db.delete();
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        DatabaseManager.closeConnection();
        dao.init();
    }

    @AfterEach
    void after() {
        DatabaseManager.closeConnection();
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/budget_test.db"));
        } catch (Exception ignore) {
        }
    }

    @Test
    void testInitIsIdempotentAndTableExists() throws Exception {
        // zweiter Aufruf darf keine Exception werfen
        dao.init();

        // Tabelle muss vorhanden sein
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='budget_transactions'")) {
            assertTrue(rs.next(), "Tabelle budget_transactions sollte existieren");
        }
    }

    @Test
    void testDeleteNonExistingDoesNotThrow() throws Exception {
        // Löschen einer nicht existierenden ID darf keine Exception werfen
        dao.deleteById(9999);
    }

    @Test
    void testInsertAndListAll() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(12.5);
        t.setDate(LocalDate.of(2025, 12, 24));
        t.setDescription("Pizza");
        t.setCategory("Essen");

        dao.insert(t);
        assertTrue(t.getId() > 0, "insert should set id");

        List<BudgetTransaction> all = dao.listAll();
        assertFalse(all.isEmpty());
        BudgetTransaction loaded = all.get(0);
        assertEquals("Pizza", loaded.getDescription());
        assertEquals("Essen", loaded.getCategory());
    }

    @Test
    void testUpdateAndDelete() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(5.0);
        t.setDate(LocalDate.of(2025, 1, 1));
        t.setDescription("Coffee");
        dao.insert(t);
        assertTrue(t.getId() > 0);

        t.setAmount(6.0);
        t.setDescription("Coffee (latte)");
        dao.update(t);

        List<BudgetTransaction> all = dao.listAll();
        BudgetTransaction up = all.stream().filter(x -> x.getId() == t.getId()).findFirst().orElse(null);
        assertNotNull(up);
        assertEquals(6.0, up.getAmount());
        assertEquals("Coffee (latte)", up.getDescription());

        dao.deleteById(t.getId());
        List<BudgetTransaction> after = dao.listAll();
        assertTrue(after.stream().noneMatch(x -> x.getId() == t.getId()));
    }

    @Test
    void testInsertWithNullDateResultsInNullOnLoad() throws Exception {
        BudgetTransaction t = new BudgetTransaction();
        t.setAmount(3.0);
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
    void testDirectInsertWithEmptyStringDateIsParsedAsNull() throws Exception {
        // direkte DB-Einfügung mit leerer Zeichenkette als date
        try (Connection conn = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = conn.prepareStatement("INSERT INTO budget_transactions(amount, date, description, category) VALUES(?,?,?,?)")) {
            ps.setDouble(1, 10.0);
            ps.setString(2, ""); // leerer String
            ps.setString(3, "EmptyDate");
            ps.setString(4, null);
            ps.executeUpdate();
        }

        List<BudgetTransaction> all = dao.listAll();
        BudgetTransaction loaded = all.stream().filter(x -> "EmptyDate".equals(x.getDescription())).findFirst().orElse(null);
        assertNotNull(loaded, "Eintrag mit leerer date-Zeichenkette sollte vorhanden sein");
        assertNull(loaded.getDate(), "Leere date-Zeichenkette muss als null interpretiert werden");
    }
}