package edu.itc.shop.web.dto;

import edu.itc.shop.domain.Order;

public record OrderResponse(Long id, String customer, Long productId, int quantity, Order.Status status) {

    public static OrderResponse of(Order o) {
        return new OrderResponse(o.getId(), o.getCustomer(), o.getProductId(), o.getQuantity(), o.getStatus());
    }
}
