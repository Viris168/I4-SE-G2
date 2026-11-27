package edu.itc.shop.domain.order;

import jakarta.ws.rs.ClientErrorException;

/** 409: the requested transition is not allowed from the order's current status. */
public class OrderStateException extends ClientErrorException {
    public OrderStateException(Order order, String action) {
        super("Order " + order.id() + " is " + order.status() + " and cannot be " + action, 409);
    }
}
