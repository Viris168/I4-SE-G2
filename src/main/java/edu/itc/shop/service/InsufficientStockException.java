package edu.itc.shop.service;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(long id, int stock, int delta) {
        super("Product " + id + " has " + stock + " units, cannot remove " + -delta);
    }

    public InsufficientStockException(String message) {
        super(message);
    }
}
