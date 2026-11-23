package edu.itc.shop.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> findAll();
    Optional<Product> findById(long id);
    Optional<Product> findBySku(String sku);
    /** Assigns an id when id == 0 and increments the version on every save. */
    Product save(Product product);
    /** @return true when a product was removed */
    boolean delete(long id);
}
