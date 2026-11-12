package edu.itc.shop.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Address;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Customer;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Tag;
import edu.itc.shop.repository.CustomerRepository;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.repository.TagRepository;

@Component
@Profile("dev")
public class DataSeeder implements ApplicationRunner {

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final TagRepository tags;

    public DataSeeder(CustomerRepository customers, ProductRepository products, OrderRepository orders, TagRepository tags) {
        this.customers = customers;
        this.products = products;
        this.orders = orders;
        this.tags = tags;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Address home = new Address("12 Strand", "London", "WC2N");
        Customer ada = customers.save(new Customer("Ada Lovelace", "ada@example.com", home, home));
        Customer grace = customers.save(new Customer("Grace Hopper", "grace@example.com", home, home));
        Customer alan = customers.save(new Customer("Alan Turing", "alan@example.com", home, home));

        Tag keyboard = tags.save(new Tag("keyboard"));
        Tag hardware = tags.save(new Tag("hardware"));
        Tag book = tags.save(new Tag("book"));
        Tag toy = tags.save(new Tag("toy"));
        Tag food = tags.save(new Tag("food"));

        List<Product> catalog = List.of(
                save(product("KB-1", "Compact Keyboard", "25.00", Category.HARDWARE, 12), keyboard, hardware),
                save(product("KB-7", "Tenkeyless Keyboard", "59.00", Category.HARDWARE, 7), keyboard, hardware),
                save(product("KB-9", "Keyboard Workstation", "140.00", Category.HARDWARE, 3), keyboard, hardware),
                save(product("MO-1", "Wireless Mouse", "20.00", Category.HARDWARE, 15), hardware),
                save(product("MN-1", "Office Monitor", "180.00", Category.HARDWARE, 0), hardware),
                save(product("CB-1", "USB-C Cable", "12.00", Category.HARDWARE, 40), hardware),
                save(product("BK-1", "Domain-Driven Design", "45.00", Category.BOOKS, 6), book),
                save(product("BK-2", "Refactoring", "40.00", Category.BOOKS, 2), book),
                save(product("TY-1", "Wooden Train", "30.00", Category.TOYS, 9), toy),
                save(product("TY-2", "Puzzle Cube", "15.00", Category.TOYS, 0), toy),
                save(product("FD-1", "Green Tea", "8.50", Category.FOOD, 20), food),
                save(product("FD-2", "Coffee Beans", "14.00", Category.FOOD, 5), food));

        List<Customer> people = List.of(ada, grace, alan);
        OrderStatus[] statuses = OrderStatus.values();
        Instant first = Instant.parse("2026-09-01T00:00:00Z");
        for (int i = 0; i < 30; i++) {
            Order order = new Order(people.get(i % people.size()));
            order.setStatus(statuses[i % statuses.length]);
            order.setPlacedAt(first.plus(Duration.ofHours(i)));
            int lineCount = 2 + (i % 3);
            for (int line = 0; line < lineCount; line++) {
                order.addLine(catalog.get((i + line) % catalog.size()), 1 + (line % 3));
            }
            orders.save(order);
        }
    }

    private static Product product(String sku, String name, String price, Category category, int stock) {
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
}
