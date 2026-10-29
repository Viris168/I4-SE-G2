package edu.itc.shop.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@ActiveProfiles("test")
@Import(OrderRepository.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldCreateAndFindOrderWithLines() {
        Long orderId = orderRepository.createOrder(1L, OrderStatus.NEW, new BigDecimal("120.00"));
        orderRepository.createOrderLine(orderId, 1L, 2, new BigDecimal("50.00"));

        Optional<Order> orderOpt = orderRepository.findOrderById(orderId);
        assertThat(orderOpt).isPresent();
        Order order = orderOpt.get();

        assertThat(order.customerId()).isEqualTo(1L);
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.total()).isEqualByComparingTo("120.00");
        assertThat(order.lines()).hasSize(1);
        assertThat(order.lines().get(0).productId()).isEqualTo(1L);
        assertThat(order.lines().get(0).qty()).isEqualTo(2);
    }

    @Test
    void shouldFindOrdersWithPagination() {
        orderRepository.createOrder(1L, OrderStatus.NEW, new BigDecimal("10.00"));
        orderRepository.createOrder(1L, OrderStatus.NEW, new BigDecimal("20.00"));
        orderRepository.createOrder(1L, OrderStatus.PAID, new BigDecimal("30.00"));

        List<Order> paged = orderRepository.findOrders(1L, OrderStatus.NEW, 0, 2);
        assertThat(paged).hasSize(2);

        long count = orderRepository.countOrders(1L, OrderStatus.NEW);
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldUpdateOrderStatus() {
        Long orderId = orderRepository.createOrder(1L, OrderStatus.NEW, new BigDecimal("15.00"));
        int updated = orderRepository.updateStatus(orderId, OrderStatus.CANCELLED);

        assertThat(updated).isEqualTo(1);
        Optional<Order> orderOpt = orderRepository.findOrderById(orderId);
        assertThat(orderOpt).isPresent();
        assertThat(orderOpt.get().status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
