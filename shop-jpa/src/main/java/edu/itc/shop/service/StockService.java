package edu.itc.shop.service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;

// Challenge: three ways to take stock without ever overselling
@Service
public class StockService {

    private static final int MAX_ATTEMPTS = 50;

    private final ProductRepository products;
    private final TransactionTemplate tx;
    private final AtomicLong optimisticRetries = new AtomicLong();

    public StockService(ProductRepository products, TransactionTemplate tx) {
        this.products = products;
        this.tx = tx;
    }

    /** Pessimistic: SELECT ... FOR UPDATE, so concurrent callers queue on the row lock. */
    @Transactional
    public void reserve(Long productId, int qty) {
        Product p = products.findForUpdate(productId).orElseThrow();
        take(p, qty);
    }

    /** Optimistic: no lock; @Version makes a concurrent commit fail, and we retry in a new transaction. */
    public void reserveOptimistic(Long productId, int qty) {
        for (int attempt = 1; ; attempt++) {
            try {
                tx.executeWithoutResult(status -> take(products.findById(productId).orElseThrow(), qty));
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                optimisticRetries.incrementAndGet();
                backOff(attempt);
            }
        }
    }

    /** Atomic: one UPDATE ... WHERE stock >= :qty; zero rows updated means out of stock. */
    @Transactional
    public void reserveAtomic(Long productId, int qty) {
        if (products.decrementStock(productId, qty) == 0) {
            throw new OutOfStockException(productId, qty);
        }
    }

    public long optimisticRetries() {
        return optimisticRetries.get();
    }

    public void resetOptimisticRetries() {
        optimisticRetries.set(0);
    }

    // random wait that grows with the attempt, so the losers do not collide again straight away
    private static void backOff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(Math.min(attempt, 10) + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void take(Product p, int qty) {
        if (p.getStock() < qty) {
            throw new OutOfStockException(p.getId(), qty);
        }
        p.setStock(p.getStock() - qty);
    }
}
