package edu.itc.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Order is the aggregate root of its lines: persisting, orphan removal and cascade REMOVE
 * all go through Order, and the SQL that Hibernate runs is counted with {@link Statistics}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class OrderCascadeTest {

    @Autowired
    private TestEntityManager em;

    private Statistics stats;
    private Customer customer;
    private Product keyboard;
    private Product mouse;

    @BeforeEach
    void setUp() {
        stats = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        Address address = new Address("1 Main St", "Phnom Penh", "12000");
        customer = em.persist(new Customer("Ada Lovelace", "ada@example.com", address, address));
        keyboard = em.persist(new Product("KB-1", "Keyboard", Money.eur("49.00"), Category.HARDWARE, 10));
        mouse = em.persist(new Product("MO-1", "Mouse", Money.eur("20.00"), Category.HARDWARE, 10));
        em.flush();
    }

    @Test
    void persistingOrderCascadesToLinesWithOneInsertEachAndNoUpdate() {
        Order order = new Order(customer);
        order.addLine(keyboard, 1);
        order.addLine(mouse, 2);

        stats.clear();
        em.persist(order);   // only the order is persisted explicitly; cascade ALL takes the lines along
        em.flush();

        assertThat(stats.getEntityStatistics(Order.class.getName()).getInsertCount()).isEqualTo(1);
        assertThat(stats.getEntityStatistics(OrderLine.class.getName()).getInsertCount()).isEqualTo(2);
        assertThat(stats.getEntityUpdateCount()).isZero();   // order_id is set before insert, no fix-up UPDATE

        em.clear();
        Order reloaded = em.find(Order.class, order.getId());
        assertThat(reloaded.getLines()).hasSize(2);
        assertThat(reloaded.total().amount()).isEqualByComparingTo(new BigDecimal("89.00")); // 49 + 2 × 20
    }

    @Test
    void removingLineFromOrderDeletesItThroughOrphanRemoval() {
        Long orderId = persistOrderWithTwoLines();
        long linesBefore = countLines();

        Order order = em.find(Order.class, orderId);
        stats.clear();
        order.removeLine(order.getLines().get(0));
        em.flush();

        assertThat(countLines()).isEqualTo(linesBefore - 1);
        assertThat(stats.getEntityStatistics(OrderLine.class.getName()).getDeleteCount()).isEqualTo(1);
        assertThat(stats.getEntityUpdateCount()).isZero();
    }

    @Test
    void removingOrderCascadesRemoveToItsRemainingLines() {
        Long orderId = persistOrderWithTwoLines();
        Order order = em.find(Order.class, orderId);
        order.removeLine(order.getLines().get(0));
        em.flush();
        em.clear();

        Order reloaded = em.find(Order.class, orderId);
        em.remove(reloaded);
        em.flush();

        assertThat(em.find(Order.class, orderId)).isNull();
        assertThat(countLines()).isZero();
    }

    private Long persistOrderWithTwoLines() {
        Order order = new Order(customer);
        order.addLine(keyboard, 1);
        order.addLine(mouse, 2);
        em.persist(order);
        em.flush();
        em.clear();
        return order.getId();
    }

    private long countLines() {
        return em.getEntityManager()
                .createQuery("select count(l) from OrderLine l", Long.class)
                .getSingleResult();
    }
}
