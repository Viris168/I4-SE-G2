package edu.itc.shop.service;

// Challenge
public class OutOfStockException extends RuntimeException {

    public OutOfStockException(Long productId, int requested) {
        super("product " + productId + ": cannot reserve " + requested + ", not enough stock");
    }
}
