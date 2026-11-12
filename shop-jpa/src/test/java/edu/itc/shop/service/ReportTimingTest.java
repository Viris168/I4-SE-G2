package edu.itc.shop.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import edu.itc.shop.support.QueryCounter;
import edu.itc.shop.support.TestData;

// Task 5, step 4: median wall time over 20 runs after a warm-up. SQL logging is switched off
// so the console does not dominate the timing. Prints the README table.
@SpringBootTest(properties = {
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "logging.level.org.hibernate.orm.jdbc.batch=OFF"})
@Tag("perf")
class ReportTimingTest {

    private static final int WARM_UP = 20;
    private static final int RUNS = 20;

    @Autowired ReportService report;
    @Autowired QueryCounter counter;
    @Autowired TestData data;

    @Test
    void measureVariants() {
        data.paidOrders(50, 3);
        Map<String, Supplier<?>> variants = new LinkedHashMap<>();
        variants.put("naive (lazy loop)", report::linesPerPaidOrder);
        variants.put("join fetch", report::linesPerPaidOrderJoinFetch);
        variants.put("@EntityGraph", report::linesPerPaidOrderEntityGraph);
        variants.put("@BatchSize(16)", report::linesPerPaidOrderBatched);

        StringBuilder table = new StringBuilder("""
                | variant                 | statements | median ms |
                |-------------------------|-----------:|----------:|
                """);
        variants.forEach((name, variant) -> {
            for (int i = 0; i < WARM_UP; i++) {
                variant.get();
            }
            counter.reset();
            variant.get();
            long statements = counter.count();
            List<Double> times = new ArrayList<>();
            for (int i = 0; i < RUNS; i++) {
                long start = System.nanoTime();
                variant.get();
                times.add((System.nanoTime() - start) / 1_000_000.0);
            }
            Collections.sort(times);
            double median = (times.get(RUNS / 2 - 1) + times.get(RUNS / 2)) / 2;
            table.append("| %-23s | %10d | %9.1f |%n".formatted(name, statements, median));
        });
        System.out.println(table);
    }
}
