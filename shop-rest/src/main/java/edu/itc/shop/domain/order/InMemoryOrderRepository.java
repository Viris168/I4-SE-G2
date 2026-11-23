package edu.itc.shop.domain.order;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe: one instance is shared by all request threads. */
public class InMemoryOrderRepository implements OrderRepository {
    private final ConcurrentMap<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(1000);

    @Override
    public String nextId() {
        return "o-" + ids.incrementAndGet();
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }
}
