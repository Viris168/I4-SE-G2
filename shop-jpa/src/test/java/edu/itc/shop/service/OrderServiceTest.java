package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Address;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Customer;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.CustomerRepository;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.web.dto.OrderDto;
import jakarta.persistence.EntityManagerFactory;

/**
 * Task 4: managed vs detached entities and the read-only trap. No @Transactional on the class:
 * each service or repository call runs in its own real transaction, like in production.
 */
@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReadOnlyTrap readOnlyTrap;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private EntityManagerFactory emf;

    private Statistics stats;

    // Rows committed by a test, deleted afterwards so other @SpringBootTest classes see an empty shop.
    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> customerIds = new ArrayList<>();

    @BeforeEach
    void statistics() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
    }

    @AfterEach
    void cleanUp() {
        orders.deleteAllById(orderIds);
        products.deleteAllById(productIds);
        customers.deleteAllById(customerIds);
    }

    @Test
    void managedChangeIsWrittenWithoutSave() {
        Long id = newOrder();

        stats.clear();
        orderService.markPaid(id);

        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2); // one SELECT, one UPDATE
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void detachedChangeIsIgnoredUntilSaveMergesIt() {
        Long id = newProduct(10);

        // The repository's own transaction ends when findById returns, so p is detached at once.
        Product p = products.findById(id).orElseThrow();
        p.setStock(99);
        assertThat(products.findById(id).orElseThrow().getStock()).isEqualTo(10);

        stats.clear();
        products.save(p); // a detached entity with an id: save() is a merge
        assertThat(stats.getEntityLoadCount()).isEqualTo(1);   // the SELECT of the merge
        assertThat(stats.getEntityUpdateCount()).isEqualTo(1); // then one UPDATE
        assertThat(products.findById(id).orElseThrow().getStock()).isEqualTo(99);
    }

    @Test
    void viewMapsLinesInsideTheReadOnlyTransaction() {
        Long id = newOrder();

        OrderDto dto = orderService.view(id);

        assertThat(dto.status()).isEqualTo(OrderStatus.NEW);
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.total().amount()).isEqualByComparingTo("25.00");
    }

    /** Documents the trap: under readOnly Hibernate uses FlushMode.MANUAL, so the change is silently lost. */
    @Test
    void changeInsideReadOnlyTransactionIsNeverFlushed() {
        Long id = newOrder();

        stats.clear();
        readOnlyTrap.cancel(id);

        assertThat(stats.getEntityUpdateCount()).isZero();
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.NEW);
    }

    private Long newProduct(int stock) {
        String sku = "T4-" + UUID.randomUUID().toString().substring(0, 8);
        Product product = new Product(sku, "Task 4 product", Money.eur("12.50"), Category.HARDWARE);
        product.setStock(stock);
        Long id = products.save(product).getId();
        productIds.add(id);
        return id;
    }

    private Long newOrder() {
        Address address = new Address("1 Russian Blvd", "Phnom Penh", "12000");
        String email = UUID.randomUUID() + "@task4.test";
        Customer customer = customers.save(new Customer("Task 4 customer", email, address, address));
        customerIds.add(customer.getId());

        Product product = products.findById(newProduct(5)).orElseThrow();
        Order order = new Order(customer);
        order.addLine(product, 2);
        Long id = orders.save(order).getId();
        orderIds.add(id);
        return id;
    }

    /** A deliberately wrong method, kept out of production code: it changes data in a read-only transaction. */
    static class ReadOnlyTrap {

        private final OrderRepository orders;

        ReadOnlyTrap(OrderRepository orders) {
            this.orders = orders;
        }

        @Transactional(readOnly = true)
        public void cancel(Long orderId) {
            orders.findById(orderId).orElseThrow().setStatus(OrderStatus.CANCELLED);
        }
    }

    @TestConfiguration
    static class TrapConfig {

        @Bean
        ReadOnlyTrap readOnlyTrap(OrderRepository orders) {
            return new ReadOnlyTrap(orders);
        }
    }
}
