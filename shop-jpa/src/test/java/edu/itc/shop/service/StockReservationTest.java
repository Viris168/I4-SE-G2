package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.support.QuietSql;

// Challenge, requirement 3: N threads race for the same product; nobody may oversell.
// SQL logging is muted (QuietSql) so the timings measure the database, not the console.
@SpringBootTest
class StockReservationTest {

    private static final Logger log = LoggerFactory.getLogger(StockReservationTest.class);

    @Autowired StockService stock;
    @Autowired ProductRepository products;
    @Autowired QuietSql quietSql;

    private final List<Long> created = new java.util.ArrayList<>();

    interface Reservation {
        void reserve(Long productId, int qty);
    }

    record Result(int ok, int outOfStock, int gaveUp, int stockAfter, long millis) {
    }

    @Test
    void pessimisticNeverOversells() throws Exception {
        Result r = race(stock::reserve, 5, 10, 1);
        log.info("pessimistic: {} ok / {} out-of-stock, stock={}, 10 threads in {} ms", r.ok, r.outOfStock, r.stockAfter, r.millis);
        assertFiveAndFive(r);
    }

    @Test
    void optimisticNeverOversells() throws Exception {
        stock.resetOptimisticRetries();
        Result r = race(stock::reserveOptimistic, 5, 10, 1);
        log.info("optimistic : {} ok / {} out-of-stock, stock={}, after {} retries, {} ms",
                r.ok, r.outOfStock, r.stockAfter, stock.optimisticRetries(), r.millis);
        assertFiveAndFive(r);
    }

    @Test
    void atomicUpdateNeverOversells() throws Exception {
        Result r = race(stock::reserveAtomic, 5, 10, 1);
        log.info("atomic upd : {} ok / {} out-of-stock, stock={}, {} ms", r.ok, r.outOfStock, r.stockAfter, r.millis);
        assertFiveAndFive(r);
    }

    /** Throughput: 10 threads x 100 reservations of 1 unit against stock 500. */
    @Test
    @Tag("perf")
    void throughput() throws Exception {
        record Variant(String name, Reservation reservation) { }
        for (Variant v : List.of(new Variant("pessimistic", stock::reserve),
                new Variant("optimistic ", stock::reserveOptimistic),
                new Variant("atomic upd ", stock::reserveAtomic))) {
            stock.resetOptimisticRetries();
            Result r = race(v.reservation(), 500, 10, 100);
            // never oversold: every successful call took exactly one unit
            assertThat(r.ok + r.stockAfter).isEqualTo(500);
            assertThat(r.stockAfter).isNotNegative();
            log.info("throughput {}: 1000 calls in {} ms = {} calls/s, {} ok, {} gave up, retries={}",
                    v.name(), r.millis, Math.round(1000_000.0 / r.millis), r.ok, r.gaveUp, stock.optimisticRetries());
        }
    }

    private Result race(Reservation reservation, int initialStock, int threads, int callsPerThread) throws Exception {
        Product p = products.save(new Product("SR-" + System.nanoTime() % 1_000_000_000L, "Race product",
                Money.eur("1.00"), Category.TOYS, initialStock));
        created.add(p.getId());

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger outOfStock = new AtomicInteger();
        AtomicInteger gaveUp = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ready.countDown();
                go.await();
                for (int i = 0; i < callsPerThread; i++) {
                    try {
                        reservation.reserve(p.getId(), 1);
                        ok.incrementAndGet();
                    } catch (OutOfStockException e) {
                        outOfStock.incrementAndGet();
                    } catch (ObjectOptimisticLockingFailureException e) {
                        gaveUp.incrementAndGet();   // optimistic only: lost MAX_ATTEMPTS times in a row
                    } catch (Throwable e) {
                        unexpected.add(e);
                    }
                }
                return null;
            });
        }
        ready.await();
        long millis;
        try (var muted = quietSql.mute()) {
            long start = System.nanoTime();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
            millis = (System.nanoTime() - start) / 1_000_000;
        }

        assertThat(unexpected).as("unexpected exceptions").isEmpty();
        int after = products.findById(p.getId()).orElseThrow().getStock();
        return new Result(ok.get(), outOfStock.get(), gaveUp.get(), after, millis);
    }

    private static void assertFiveAndFive(Result r) {
        assertThat(r.ok).isEqualTo(5);
        assertThat(r.outOfStock).isEqualTo(5);
        assertThat(r.stockAfter).isZero();
        assertThat(r.gaveUp).isZero();
    }

    @AfterEach
    void cleanUp() {
        products.deleteAllById(created);
    }
}
