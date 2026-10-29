package edu.itc.shop.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final JdbcClient jdbcClient;

    public OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Long createOrder(Long customerId, OrderStatus status, BigDecimal total) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO orders (customer_id, status, placed_at, total)
                VALUES (:customerId, :status, :placedAt, :total)
                """)
                .param("customerId", customerId)
                .param("status", status.name())
                .param("placedAt", Timestamp.from(Instant.now()))
                .param("total", total)
                .update(keyHolder, "id");

        return keyHolder.getKey().longValue();
    }

    public Long insert(Long customerId, OrderStatus status, BigDecimal total, List<OrderLine> lines) {
        Long orderId = createOrder(customerId, status, total);
        if (lines != null) {
            for (OrderLine line : lines) {
                createOrderLine(orderId, line.productId(), line.qty(), line.unitPrice());
            }
        }
        return orderId;
    }

    public void createOrderLine(Long orderId, Long productId, int qty, BigDecimal unitPrice) {
        jdbcClient.sql("""
                INSERT INTO order_line (order_id, product_id, qty, unit_price)
                VALUES (:orderId, :productId, :qty, :unitPrice)
                """)
                .param("orderId", orderId)
                .param("productId", productId)
                .param("qty", qty)
                .param("unitPrice", unitPrice)
                .update();
    }

    public Optional<Order> findOrderById(Long id) {
        List<Order> orders = jdbcClient.sql("""
                SELECT id, customer_id AS customerId, status, placed_at AS placedAt, total
                FROM orders
                WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new Order(
                        rs.getLong("id"),
                        rs.getLong("customerId"),
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("placedAt").toInstant(),
                        rs.getBigDecimal("total")
                ))
                .list();

        if (orders.isEmpty()) {
            return Optional.empty();
        }

        Order order = orders.get(0);
        List<OrderLine> lines = findLinesByOrderId(order.id());
        return Optional.of(new Order(order.id(), order.customerId(), order.status(), order.placedAt(), order.total(), lines));
    }

    public List<OrderLine> findLinesByOrderId(Long orderId) {
        return jdbcClient.sql("""
                SELECT id, order_id AS orderId, product_id AS productId, qty, unit_price AS unitPrice
                FROM order_line
                WHERE order_id = :orderId
                """)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new OrderLine(
                        rs.getLong("id"),
                        rs.getLong("orderId"),
                        rs.getLong("productId"),
                        rs.getInt("qty"),
                        rs.getBigDecimal("unitPrice")
                ))
                .list();
    }

    public int updateStatus(Long orderId, OrderStatus newStatus) {
        return jdbcClient.sql("""
                UPDATE orders
                SET status = :status
                WHERE id = :id
                """)
                .param("id", orderId)
                .param("status", newStatus.name())
                .update();
    }

    public List<Order> findOrders(Long customerId, OrderStatus status, int page, int size) {
        int offset = page * size;
        String statusStr = status != null ? status.name() : null;

        return jdbcClient.sql("""
                SELECT id, customer_id AS customerId, status, placed_at AS placedAt, total
                FROM orders
                WHERE (:customerId IS NULL OR customer_id = :customerId)
                  AND (:status IS NULL OR status = :status)
                ORDER BY placed_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("customerId", customerId)
                .param("status", statusStr)
                .param("limit", size)
                .param("offset", offset)
                .query((rs, rowNum) -> new Order(
                        rs.getLong("id"),
                        rs.getLong("customerId"),
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("placedAt").toInstant(),
                        rs.getBigDecimal("total")
                ))
                .list();
    }

    public long countOrders(Long customerId, OrderStatus status) {
        String statusStr = status != null ? status.name() : null;

        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM orders
                WHERE (:customerId IS NULL OR customer_id = :customerId)
                  AND (:status IS NULL OR status = :status)
                """)
                .param("customerId", customerId)
                .param("status", statusStr)
                .query(Long.class)
                .single();
    }
}
