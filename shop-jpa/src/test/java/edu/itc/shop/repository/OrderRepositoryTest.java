package edu.itc.shop.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import edu.itc.shop.domain.Address;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Customer;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Tag;
import edu.itc.shop.web.dto.OrderSummary;
import edu.itc.shop.web.dto.ProductView;
import jakarta.persistence.EntityManager;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private TagRepository tags;

    @Autowired
    private EntityManager em;

    @Test
    void derivedQueries() {
        Customer ada = customers.save(customer("Ada Lovelace", "ada@example.com"));
        Customer grace = customers.save(customer("Grace Hopper", "grace@example.com"));
        Tag keyboard = tags.save(new Tag("keyboard"));

        Product expensive = save(product("KB-9", "Workstation Keyboard", "140.00", Category.HARDWARE, 4), keyboard);
        Product mid = save(product("KB-7", "Tenkeyless Keyboard", "59.00", Category.HARDWARE, 3), keyboard);
        Product cheap = save(product("KB-1", "Compact Keyboard", "25.00", Category.HARDWARE, 8));
        Product mouse = save(product("MO-1", "Wireless Mouse", "20.00", Category.HARDWARE, 1));
        save(product("BK-1", "Refactoring", "40.00", Category.BOOKS, 2));

        Order olderPaid = order(ada, OrderStatus.PAID, "2026-09-23T10:00:00Z", cheap, 1);
        Order newerPaid = order(ada, OrderStatus.PAID, "2026-09-24T10:00:00Z", mid, 2);
        Order shipped = order(ada, OrderStatus.SHIPPED, "2026-09-25T10:00:00Z", mouse, 1);
        order(grace, OrderStatus.PAID, "2026-09-24T12:00:00Z", expensive, 1);

        List<Order> adaPaid = orders.findByCustomerEmailAndStatusOrderByPlacedAtDesc("ada@example.com", OrderStatus.PAID);

        assertThat(adaPaid).extracting(Order::getId).containsExactly(newerPaid.getId(), olderPaid.getId());
        assertThat(orders.countByStatus(OrderStatus.PAID)).isEqualTo(3);
        assertThat(orders.findFirstByCustomerIdOrderByPlacedAtDesc(ada.getId()))
                .get()
                .extracting(Order::getId)
                .isEqualTo(shipped.getId());
        assertThat(products.existsBySku("KB-1")).isTrue();
        assertThat(products.existsBySku("NOPE")).isFalse();
        assertThat(products.findTop3ByCategoryOrderByPriceAmountDesc(Category.HARDWARE))
                .extracting(Product::getSku)
                .containsExactly("KB-9", "KB-7", "KB-1");
        assertThat(products.findByTagsName("keyboard"))
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("KB-9", "KB-7");
    }

    @Test
    void fetchJoinInitializesLinesAndPlainFindByIdDoesNot() {
        Customer ada = customers.save(customer("Ada Lovelace", "ada@example.com"));
        Product keyboard = save(product("KB-1", "Compact Keyboard", "25.00", Category.HARDWARE, 5));
        Product mouse = save(product("MO-1", "Wireless Mouse", "20.00", Category.HARDWARE, 5));
        Order order = new Order(ada);
        order.addLine(keyboard, 1);
        order.addLine(mouse, 2);
        orders.save(order);
        em.flush();
        em.clear();

        List<Order> fetched = orders.findWithLinesByCustomerEmail("ada@example.com");

        assertThat(fetched).hasSize(1);
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(fetched.get(0), "lines")).isTrue();
        assertThat(fetched.get(0).getLines()).hasSize(2);
        Long id = fetched.get(0).getId();

        em.clear();
        Order plain = orders.findById(id).orElseThrow();
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(plain, "lines")).isFalse();
    }

    @Test
    void countLinesByStatusCountsLinesOfMatchingOrders() {
        Customer ada = customers.save(customer("Ada Lovelace", "ada@example.com"));
        Product keyboard = save(product("KB-1", "Compact Keyboard", "25.00", Category.HARDWARE, 5));
        Product mouse = save(product("MO-1", "Wireless Mouse", "20.00", Category.HARDWARE, 5));
        Order paid = new Order(ada);
        paid.setStatus(OrderStatus.PAID);
        paid.addLine(keyboard, 1);
        paid.addLine(mouse, 1);
        orders.save(paid);
        Order fresh = new Order(ada);
        fresh.addLine(keyboard, 3);
        orders.save(fresh);

        assertThat(orders.countLinesByStatus(OrderStatus.PAID)).isEqualTo(2);
        assertThat(orders.countLinesByStatus(OrderStatus.NEW)).isEqualTo(1);
        assertThat(orders.countLinesByStatus(OrderStatus.CANCELLED)).isZero();
    }

    @Test
    void nativeFindLowStockReturnsProductsBelowThresholdOrderedByStock() {
        save(product("MN-1", "Office Monitor", "180.00", Category.HARDWARE, 0));
        save(product("KB-1", "Compact Keyboard", "25.00", Category.HARDWARE, 2));
        save(product("MO-1", "Wireless Mouse", "20.00", Category.HARDWARE, 9));

        assertThat(products.findLowStock(5))
                .extracting(Product::getSku)
                .containsExactly("MN-1", "KB-1");
    }

    @Test
    void projectionsAndSpecificationFilter() {
        Customer ada = customers.save(customer("Ada Lovelace", "ada@example.com"));
        Customer grace = customers.save(customer("Grace Hopper", "grace@example.com"));
        Product keyboard = save(product("KB-1", "Compact Keyboard", "25.00", Category.HARDWARE, 12));
        Product tenkey = save(product("KB-7", "Tenkeyless Keyboard", "59.00", Category.HARDWARE, 7));
        save(product("KB-9", "Keyboard Workstation", "140.00", Category.HARDWARE, 3));
        save(product("MO-1", "Wireless Mouse", "20.00", Category.HARDWARE, 15));
        save(product("MN-1", "Office Monitor", "180.00", Category.HARDWARE, 0));
        Product book = save(product("BK-1", "Keyboard Shortcuts", "30.00", Category.BOOKS, 4));
        save(product("BK-2", "Refactoring", "40.00", Category.BOOKS, 2));

        Order newer = order(ada, OrderStatus.PAID, "2026-09-24T09:00:00Z", keyboard, 1);
        order(grace, OrderStatus.PAID, "2026-09-23T09:00:00Z", tenkey, 1);
        order(ada, OrderStatus.NEW, "2026-09-25T09:00:00Z", book, 1);

        var page = orders.findByStatus(OrderStatus.PAID, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "placedAt")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        OrderSummary summary = page.getContent().get(0);
        assertThat(summary.getId()).isEqualTo(newer.getId());
        assertThat(summary.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(summary.getPlacedAt()).isEqualTo(Instant.parse("2026-09-24T09:00:00Z"));
        assertThat(summary.getCustomer().getName()).isEqualTo("Ada Lovelace");

        var books = products.findByCategory(Category.BOOKS, PageRequest.of(0, 10));
        assertThat(books.getContent())
                .extracting(ProductView::sku)
                .containsExactlyInAnyOrder("BK-1", "BK-2");
        assertThat(books.getContent())
                .filteredOn(view -> view.sku().equals("BK-1"))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.amount()).isEqualByComparingTo(new BigDecimal("30.00"));
                    assertThat(view.stock()).isEqualTo(4);
                    assertThat(view.name()).isEqualTo("Keyboard Shortcuts");
                });

        var filtered = products.findAll(
                ProductSpecs.filter("key", Category.HARDWARE, new BigDecimal("60"), true),
                PageRequest.of(0, 20));
        assertThat(filtered.getContent()).extracting(Product::getSku).containsExactlyInAnyOrder("KB-1", "KB-7");

        assertThat(products.findAll(ProductSpecs.filter(null, null, null, null), PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(7);
        assertThat(products.findAll(ProductSpecs.filter(null, null, null, false), PageRequest.of(0, 20)).getContent())
                .extracting(Product::getSku)
                .containsExactly("MN-1");
        assertThat(products.findAll(ProductSpecs.filter("   ", Category.BOOKS, null, null), PageRequest.of(0, 20)).getContent())
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("BK-1", "BK-2");
    }

    private Customer customer(String name, String email) {
        Address address = new Address("1 Main", "Phnom Penh", "12000");
        return new Customer(name, email, address, address);
    }

    private Product product(String sku, String name, String price, Category category, int stock) {
        Product product = new Product(sku, name, Money.eur(price), category);
        product.setStock(stock);
        return product;
    }

    private Product save(Product product, Tag... productTags) {
        for (Tag tag : productTags) {
            product.tag(tag);
        }
        return products.save(product);
    }

    private Order order(Customer customer, OrderStatus status, String placedAt, Product product, int quantity) {
        Order order = new Order(customer);
        order.setStatus(status);
        order.setPlacedAt(Instant.parse(placedAt));
        order.addLine(product, quantity);
        return orders.save(order);
    }
}
