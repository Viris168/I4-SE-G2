package edu.itc.shop.domain;

/** Thrown when an order cannot move to the requested status (for example shipping it twice). */
public class OrderStateException extends RuntimeException {

    public OrderStateException(String message) {
        super(message);
    }
}
