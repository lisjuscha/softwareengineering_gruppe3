package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.BudgetTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
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
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/budget_test.db")); } catch (Exception ignore) {}
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
}

