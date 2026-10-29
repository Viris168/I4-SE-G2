package edu.itc.shop.order;

import java.math.BigDecimal;

public record OrderLine(
        Long id,
        Long orderId,
        Long productId,
        int qty,
        BigDecimal unitPrice
) {
    public OrderLine(Long productId, int qty, BigDecimal unitPrice) {
        this(null, null, productId, qty, unitPrice);
    }
}
