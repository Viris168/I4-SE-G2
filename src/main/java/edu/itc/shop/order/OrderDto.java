package edu.itc.shop.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
        Long id,
        Long customerId,
        OrderStatus status,
        Instant placedAt,
        BigDecimal total,
        List<OrderLineDto> lines
) {
    public record OrderLineDto(Long productId, int qty, BigDecimal unitPrice) {}
}
