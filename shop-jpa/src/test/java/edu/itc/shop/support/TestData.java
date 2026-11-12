package edu.itc.shop.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Address;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Customer;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.domain.Product;

// Task 5
/** Seeds PAID orders for the N+1 tests. Idempotent: the H2 database is shared by every test context. */
@Component
public class TestData {

    public static final int PRODUCTS = 30;
    private static final String EMAIL = "nplusone@test.local";

    private final EntityManager em;

    public TestData(EntityManager em) {
        this.em = em;
    }

    /** {@code count} PAID orders with {@code linesEach} lines each, spread over {@value #PRODUCTS} products. */
    @Transactional
    public List<Long> paidOrders(int count, int linesEach) {
        List<Long> existing = em.createQuery(
                        "select o.id from Order o where o.customer.email = :email order by o.id", Long.class)
                .setParameter("email", EMAIL)
                .getResultList();
        if (!existing.isEmpty()) {
            return existing;
        }

        Address address = new Address("1 Test Street", "Phnom Penh", "12000");
        Customer customer = new Customer("N+1 Tester", EMAIL, address, address);
        em.persist(customer);

        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= PRODUCTS; i++) {
            Product p = new Product("NP-%03d".formatted(i), "Report product " + i,
                    Money.eur("10.00"), Category.HARDWARE, 100);
            em.persist(p);
            products.add(p);
        }

        List<Order> orders = new ArrayList<>();
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            Order order = new Order(customer);
            order.setStatus(OrderStatus.PAID);
            order.setPlacedAt(start.plusSeconds(i * 60L));
            for (int k = 0; k < linesEach; k++) {
                order.addLine(products.get((i * linesEach + k) % PRODUCTS), 1);
            }
            em.persist(order);
            orders.add(order);
        }
        em.flush();
        return orders.stream().map(Order::getId).toList();
    }
}
