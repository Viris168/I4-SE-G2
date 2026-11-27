package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

import edu.itc.shop.domain.Address;
import edu.itc.shop.domain.Customer;
import edu.itc.shop.domain.Order;
import edu.itc.shop.repository.CustomerRepository;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.web.dto.FeedResponse;
import edu.itc.shop.web.dto.OrderSummary;

// Challenge, requirement 2. The shared H2 database may already hold orders from other test classes,
// so the assertions walk the whole feed instead of expecting fixed ids.
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(OrderFeedService.class)
class OrderFeedTest {

    @Autowired OrderFeedService feed;
    @Autowired OrderRepository orders;
    @Autowired CustomerRepository customers;

    @BeforeEach
    void seed() {
        Address a = new Address("1 Feed St", "Phnom Penh", "12000");
        Customer c = customers.save(new Customer("Feed Tester", "feed@test.local", a, a));
        Instant base = Instant.parse("2027-01-01T00:00:00Z");
        for (int i = 0; i < 45; i++) {
            Order o = new Order(c);
            o.setPlacedAt(base.plusSeconds(i / 3));   // three orders per timestamp: the id breaks ties
            orders.save(o);
        }
        orders.flush();
    }

    @Test
    void firstPageHasCursorOfLastItem() {
        FeedResponse<OrderSummary> page = feed.keyset(null, 20);

        assertThat(page.items()).hasSize(20);
        assertThat(page.hasNext()).isTrue();
        OrderSummary last = page.items().get(19);
        assertThat(page.nextCursor()).isEqualTo(last.getPlacedAt() + "," + last.getId());
    }

    @Test
    void keysetAndSliceWalkTheSameOrdersNewestFirstWithoutGapsOrDuplicates() {
        List<Long> keyset = walk(true);
        List<Long> slice = walk(false);

        assertThat(keyset).hasSize((int) orders.count()).doesNotHaveDuplicates();
        assertThat(keyset).isEqualTo(slice);
        List<Order> all = orders.findAllById(keyset);
        all.sort(Comparator.comparing(Order::getPlacedAt).thenComparing(Order::getId).reversed());
        assertThat(keyset).isEqualTo(all.stream().map(Order::getId).toList());
    }

    private List<Long> walk(boolean keyset) {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        do {
            FeedResponse<OrderSummary> page = keyset ? feed.keyset(cursor, 7) : feed.slice(cursor, 7);
            page.items().forEach(item -> ids.add(item.getId()));
            cursor = page.nextCursor();
        } while (cursor != null);
        return ids;
    }
}
