package edu.itc.shop.domain.order;

import java.math.BigDecimal;
import java.util.List;

public record Order(String id, List<OrderLine> lines, BigDecimal total, OrderStatus status) {

    public Order {
        lines = List.copyOf(lines);
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, lines, total, newStatus);
    }
}
