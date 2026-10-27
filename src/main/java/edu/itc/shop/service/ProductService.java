package edu.itc.shop.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.repository.ProductRepository;

@Service
public class ProductService {
    private final ProductRepository repository;
    public ProductService(ProductRepository repository) { this.repository = repository; }

    /** Moves {@code qty} units from one product to another; both updates commit or neither does. */
    @Transactional
    public void restock(long fromId, long toId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive, was " + qty);
        }
        if (repository.updateStock(fromId, -qty) == 0) {
            throw new IllegalStateException("Product " + fromId + " does not exist or has fewer than " + qty + " units");
        }
        if (repository.updateStock(toId, qty) == 0) {
            throw new IllegalStateException("Product " + toId + " does not exist");
        }
    }
}
