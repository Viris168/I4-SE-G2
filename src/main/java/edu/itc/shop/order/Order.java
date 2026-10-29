package edu.itc.shop.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(
        Long id,
        Long customerId,
        OrderStatus status,
        Instant placedAt,
        BigDecimal total,
        List<OrderLine> lines
) {
    public Order(Long id, Long customerId, OrderStatus status, Instant placedAt, BigDecimal total) {
        this(id, customerId, status, placedAt, total, List.of());
    }
}
