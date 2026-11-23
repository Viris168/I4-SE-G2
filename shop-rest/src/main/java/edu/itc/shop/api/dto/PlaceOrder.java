package edu.itc.shop.api.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import edu.itc.shop.domain.order.OrderLine;

/** Body of POST /orders: {"lines":[{"productId":1,"quantity":2}]}. */
public record PlaceOrder(@NotEmpty List<@Valid @NotNull Line> lines) {

    public record Line(@Positive long productId, @Min(1) int quantity) {
        public static Line from(OrderLine l) { return new Line(l.productId(), l.quantity()); }
        public OrderLine toOrderLine() { return new OrderLine(productId, quantity); }
    }

    public List<OrderLine> toOrderLines() {
        return lines.stream().map(Line::toOrderLine).toList();
    }
}
