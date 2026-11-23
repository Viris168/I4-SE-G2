package edu.itc.shop.domain;

import java.util.List;
import jakarta.ws.rs.NotFoundException;

/**
 * Business rules (unique SKU, no negative stock) live here, not in the resource.
 * Writes are synchronized so check-then-save stays atomic across request threads.
 */
public class ProductService {
    private final ProductRepository repo;

    public ProductService(ProductRepository repo) { this.repo = repo; }

    public List<Product> list() {
        return repo.findAll();
    }

    public Product get(long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    public synchronized Product create(Product p) {
        repo.findBySku(p.sku()).ifPresent(existing -> { throw new DuplicateSkuException(p.sku()); });
        return repo.save(new Product(0, p.name(), p.sku(), p.price(), p.stock(), 0));
    }

    public synchronized Product replace(long id, Product changes) {
        Product current = get(id);
        repo.findBySku(changes.sku())
            .filter(other -> other.id() != id)
            .ifPresent(other -> { throw new DuplicateSkuException(changes.sku()); });
        return repo.save(new Product(id, changes.name(), changes.sku(), changes.price(),
                                     changes.stock(), current.version()));
    }

    public synchronized Product adjustStock(long id, int delta) {
        Product current = get(id);
        int newStock = current.stock() + delta;
        if (newStock < 0) throw new InsufficientStockException(id, current.stock(), delta);
        return repo.save(current.withStock(newStock));
    }

    public synchronized void delete(long id) {
        if (!repo.delete(id)) throw new NotFoundException("Product " + id + " not found");
    }
}
