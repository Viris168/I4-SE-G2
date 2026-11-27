package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import edu.itc.shop.support.QuietSql;
import edu.itc.shop.web.dto.FeedResponse;
import edu.itc.shop.web.dto.OrderSummary;

// Challenge, requirement 2: the feed near the top vs at offset 100 000, three implementations.
// Inserts 200 400 orders with plain JDBC (ids far above the sequence) and deletes them afterwards.
// Every timed call asks for a different page: H2 returns the cached result of an identical query
// on unchanged tables, which would make repeated calls look free.
@SpringBootTest
@Tag("perf")
class FeedPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(FeedPerformanceTest.class);
    private static final long FIRST_ID = 900_000_000L;
    private static final int ORDERS = 200_400;
    private static final int SIZE = 20;
    private static final Instant NEWEST = Instant.parse("2099-01-01T00:00:00Z");

    @Autowired OrderFeedService feed;
    @Autowired JdbcTemplate jdbc;
    @Autowired QuietSql quietSql;

    @Test
    void keysetStaysFastDeepIntoTheFeed() {
        jdbc.update("insert into customer (id, name, email, street, city, zip, ship_street, ship_city, ship_zip) "
                + "values (?, 'Perf', 'perf@test.local', 's', 'c', 'z', 's', 'c', 'z')", FIRST_ID);
        // newer than every other order in the database: row k of the feed is (NEWEST - k s, FIRST_ID + k)
        List<Object[]> rows = new ArrayList<>();
        for (int k = 0; k < ORDERS; k++) {
            rows.add(new Object[] {FIRST_ID + k, FIRST_ID, Timestamp.from(NEWEST.minusSeconds(k))});
        }
        jdbc.batchUpdate("insert into orders (id, customer_id, status, placed_at, version) values (?, ?, 'PAID', ?, 0)", rows);

        int deep = 100_000 / SIZE;
        assertThat(ids(feed.keyset(cursor(deep), SIZE)))
                .isEqualTo(ids(feed.slice(String.valueOf(deep), SIZE)))
                .isEqualTo(ids(feed.seek(cursor(deep), SIZE)))
                .startsWith(FIRST_ID + 100_000);

        try (var muted = quietSql.mute()) {
            log.info("| variant                     | offset 20-400 | offset 100 000 |");
            row("Slice (OFFSET)", p -> feed.slice(String.valueOf(p), SIZE), deep);
            row("Window keyset (Spring Data)", p -> feed.keyset(cursor(p), SIZE), deep);
            row("row-value keyset (seek)", p -> feed.seek(cursor(p), SIZE), deep);
        }

        plan("slice", "select o1_0.id from orders o1_0 left join customer c1_0 on c1_0.id=o1_0.customer_id "
                + "order by o1_0.placed_at desc,o1_0.id desc offset 100000 rows fetch first 21 rows only");
        plan("window", "select o1_0.id from orders o1_0 "
                + "where o1_0.placed_at<" + ts(99_999) + " or o1_0.placed_at=" + ts(99_999) + " and o1_0.id<" + (FIRST_ID + 99_999)
                + " order by o1_0.placed_at desc,o1_0.id desc fetch first 21 rows only");
        plan("seek", "select o1_0.id from orders o1_0 left join customer c1_0 on c1_0.id=o1_0.customer_id "
                + "where (o1_0.placed_at,o1_0.id)<(" + ts(99_999) + "," + (FIRST_ID + 99_999) + ")"
                + " order by o1_0.placed_at desc,o1_0.id desc fetch first 21 rows only");
    }

    /** Cursor that makes the next page start at page {@code p}: the last row of page p - 1. */
    private static String cursor(int page) {
        int k = page * SIZE - 1;
        return NEWEST.minusSeconds(k) + "," + (FIRST_ID + k);
    }

    private static String ts(int k) {
        return "TIMESTAMP WITH TIME ZONE '" + NEWEST.minusSeconds(k).toString().replace('T', ' ') + "'";
    }

    private static List<Long> ids(FeedResponse<OrderSummary> page) {
        return page.items().stream().map(OrderSummary::getId).toList();
    }

    private void row(String name, IntFunction<?> call, int deep) {
        log.info("| {} | {} ms | {} ms |", "%-27s".formatted(name),
                "%9.2f".formatted(median(call, 1)), "%10.2f".formatted(median(call, deep)));
    }

    /** Median of 15 calls, each for a different page starting at {@code firstPage}, after 5 warm-up pages. */
    private static double median(IntFunction<?> call, int firstPage) {
        for (int i = 0; i < 5; i++) {
            call.apply(firstPage + 15 + i);
        }
        List<Double> times = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            long start = System.nanoTime();
            call.apply(firstPage + i);
            times.add((System.nanoTime() - start) / 1_000_000.0);
        }
        Collections.sort(times);
        return times.get(times.size() / 2);
    }

    private void plan(String name, String sql) {
        String plan = jdbc.queryForObject("explain analyze " + sql, String.class);
        log.info("EXPLAIN {}: {}", name, plan.replaceAll("\\s+", " "));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from orders where customer_id = ?", FIRST_ID);
        jdbc.update("delete from customer where id = ?", FIRST_ID);
    }
}
