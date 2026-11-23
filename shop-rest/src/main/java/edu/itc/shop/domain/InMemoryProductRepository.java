package edu.itc.shop.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe: one instance is shared by all request threads. */
public class InMemoryProductRepository implements ProductRepository {
    private final ConcurrentMap<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public InMemoryProductRepository() {
        save(new Product(0, "Kettle", "KT-01", new BigDecimal("24.90"), 12, 0));
        save(new Product(0, "Mug", "MG-01", new BigDecimal("6.50"), 40, 0));
        save(new Product(0, "Tea", "TE-01", new BigDecimal("4.20"), 0, 0));
    }

    @Override
    public List<Product> findAll() {
        return products.values().stream()
                .sorted(Comparator.comparingLong(Product::id))
                .toList();
    }

    @Override
    public Optional<Product> findById(long id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return products.values().stream()
                .filter(p -> p.sku().equals(sku))
                .findFirst();
    }

    @Override
    public Product save(Product product) {
        long id = product.id() == 0 ? ids.incrementAndGet() : product.id();
        // compute() is atomic per key, so concurrent saves of one id cannot lose a version bump
        return products.compute(id, (key, current) -> {
            int version = current == null ? product.version() : current.version();
            return product.withId(key).withVersion(version + 1);
        });
    }

    @Override
    public boolean delete(long id) {
        return products.remove(id) != null;
    }
}
