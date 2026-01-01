package com.flatmanager.dao;

import com.flatmanager.database.DatabaseManager;
import com.flatmanager.model.CleaningTask;
import com.flatmanager.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class DaoConcurrencyTest {
    private static final String DB_FILE = "target/dao_concurrency_test.db";

    @BeforeEach
    public void setup() throws Exception {
        File db = new File(DB_FILE);
        if (db.exists()) Files.delete(db.toPath());
        System.setProperty("db.url", "jdbc:sqlite:" + db.getAbsolutePath());
        Database.closeConnection();
        Database.init();
    }

    @AfterEach
    public void teardown() throws Exception {
        Database.closeConnection();
        try { Files.deleteIfExists(new File(DB_FILE).toPath()); } catch (Exception ignored) {}
    }

    @Test
    public void testConcurrentInsertsAndUpdates() throws Exception {
        // prepare DAOs
        CleaningTaskDao cleaningDao = new CleaningTaskDao();
        cleaningDao.init();

        // number of threads and ops
        int writerThreads = 8;
        int insertsPerThread = 50;

        ExecutorService ex = Executors.newFixedThreadPool(writerThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(writerThreads);

        // Each thread will insert cleaning tasks and then update some
        for (int t = 0; t < writerThreads; t++) {
            final int tid = t;
            ex.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < insertsPerThread; i++) {
                        CleaningTask c = new CleaningTask("CT-" + tid + "-" + i, LocalDate.now().plusDays(i), "user" + (i % 3), "Wöchentlich", false);
                        cleaningDao.insert(c);
                        // occasionally update
                        if (i % 10 == 0) {
                            c.setCompleted(true);
                            cleaningDao.update(c);
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    fail("Exception in writer thread: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // start threads
        startLatch.countDown();

        // wait for completion
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        ex.shutdownNow();
        assertTrue(finished, "Writer threads did not finish in time");

        // verify number of inserted tasks
        int expected = writerThreads * insertsPerThread;
        int count = 0;
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM cleaning_tasks")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) count = rs.getInt(1);
            }
        }
        assertEquals(expected, count, "Expected all cleaning tasks to be inserted");

        // verify no duplicate IDs and all due dates set
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"));
             PreparedStatement ps = c.prepareStatement("SELECT id, due FROM cleaning_tasks")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                    assertNotNull(rs.getString(2));
                }
                // simple uniqueness check
                long unique = ids.stream().distinct().count();
                assertEquals(ids.size(), unique, "IDs should be unique");
            }
        }
    }
}

