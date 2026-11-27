package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import edu.itc.shop.domain.Category;
import edu.itc.shop.support.QuietSql;

// Challenge, requirement 4: 50 000 products three ways. SQL logging is muted (QuietSql): logging
// 50 000 INSERTs and 400 000 bind parameters would dominate the time.
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BulkImportTest {

    private static final Logger log = LoggerFactory.getLogger(BulkImportTest.class);
    private static final int ROWS = 50_000;

    @Autowired BulkImportService importer;
    @Autowired TransactionTemplate tx;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired QuietSql quietSql;

    @TempDir Path dir;
    private String prefix;
    private static boolean warmedUp;

    interface Import {
        int run(Path csv) throws IOException;
    }

    @Test
    @Order(1)
    void statelessInsertMultiple() throws IOException {
        Run r = measure("BS", importer::importCsv);
        log.info("stateless insertMultiple(500): {} rows in {} s, {} JDBC statements, heap +{} MB",
                r.rows, r.seconds(), r.statements, r.heapMb);
        assertThat(r.millis).isLessThan(10_000);
    }

    @Test
    @Order(2)
    void saveAllOneTransactionNoClear() throws IOException {
        Run r = measure("BN", csv -> importer.importWithSaveAll(csv, false));
        log.info("saveAll, one tx, no clear   : {} rows in {} s, {} JDBC statements, heap +{} MB",
                r.rows, r.seconds(), r.statements, r.heapMb);
    }

    @Test
    @Order(3)
    void saveAllWithFlushClearEvery500() throws IOException {
        Run r = measure("BF", csv -> importer.importWithSaveAll(csv, true));
        log.info("saveAll + flush/clear(500)  : {} rows in {} s, {} JDBC statements, heap +{} MB",
                r.rows, r.seconds(), r.statements, r.heapMb);
    }

    record Run(int rows, long millis, long statements, long heapMb) {
        String seconds() {
            return "%.1f".formatted(millis / 1000.0);
        }
    }

    /** Runs every variant once on 5 000 rows first, so the JIT does not favour whichever runs last. */
    @org.junit.jupiter.api.BeforeEach
    void warmUp() throws IOException {
        if (warmedUp) {
            return;
        }
        try (var muted = quietSql.mute()) {
            for (Import variant : new Import[] {importer::importCsv,
                    csv -> importer.importWithSaveAll(csv, false), csv -> importer.importWithSaveAll(csv, true)}) {
                prefix = "BW" + Long.toString(System.nanoTime() % 100_000, 36) + "-";
                variant.run(generate(prefix, 5_000));
                cleanUp();
            }
        }
        warmedUp = true;
    }

    private Run measure(String runPrefix, Import importer) throws IOException {
        prefix = runPrefix + Long.toString(System.nanoTime() % 100_000, 36) + "-";
        Path csv = generate(prefix, ROWS);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        System.gc();
        long baseline = memory.getHeapMemoryUsage().getUsed();
        AtomicLong peak = new AtomicLong(baseline);
        Thread sampler = Thread.ofPlatform().daemon().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                peak.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        int rows;
        long millis;
        try (var muted = quietSql.mute()) {
            stats.clear();
            long start = System.nanoTime();
            rows = importer.run(csv);
            millis = (System.nanoTime() - start) / 1_000_000;
        } finally {
            sampler.interrupt();
        }

        assertThat(rows).isEqualTo(ROWS);
        Long stored = tx.execute(s -> em.createQuery(
                "select count(p) from Product p where p.sku like :prefix", Long.class)
                .setParameter("prefix", prefix + "%").getSingleResult());
        assertThat(stored).isEqualTo(ROWS);
        return new Run(rows, millis, stats.getPrepareStatementCount(), (peak.get() - baseline) / (1024 * 1024));
    }

    private Path generate(String skuPrefix, int rows) throws IOException {
        Path csv = dir.resolve(skuPrefix + "catalogue.csv");
        Category[] categories = Category.values();
        try (BufferedWriter out = Files.newBufferedWriter(csv)) {
            out.write("sku;name;amount;currency;category;stock\n");
            for (int i = 0; i < rows; i++) {
                out.write("%s%06d;Supplier product %d;%d.99;EUR;%s;%d%n"
                        .formatted(skuPrefix, i, i, 1 + i % 500, categories[i % categories.length], i % 50));
            }
        }
        return csv;
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(s -> em.createQuery("delete from Product p where p.sku like :prefix")
                .setParameter("prefix", prefix + "%").executeUpdate());
    }
}
