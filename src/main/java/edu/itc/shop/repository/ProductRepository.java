package edu.itc.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import edu.itc.shop.domain.Product;

@Repository
public class ProductRepository {

    private final JdbcClient jdbc;

    public ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Product> findAll() {
        return jdbc.sql("SELECT id, name, price, stock FROM product ORDER BY id")
                .query(Product.class)
                .list();
    }

    public Optional<Product> findById(long id) {
        return jdbc.sql("SELECT id, name, price, stock FROM product WHERE id = :id")
                .param("id", id)
                .query(Product.class)
                .optional();
    }

    public List<Product> findByNameContaining(String text) {
        return jdbc.sql("SELECT id, name, price, stock FROM product WHERE LOWER(name) LIKE LOWER(:pattern) ORDER BY id")
                .param("pattern", "%" + text + "%")
                .query(Product.class)
                .list();
    }

    public List<Product> findByStockLessThan(int threshold) {
        return jdbc.sql("SELECT id, name, price, stock FROM product WHERE stock < :threshold ORDER BY id")
                .param("threshold", threshold)
                .query(Product.class)
                .list();
    }

    public boolean existsByName(String name) {
        return jdbc.sql("SELECT COUNT(*) FROM product WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .single() > 0;
    }

    public long insert(Product p) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO product(name, price, stock) VALUES (:name, :price, :stock)")
                .paramSource(p)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public int updateStock(long id, int delta) {
        return jdbc.sql("UPDATE product SET stock = stock + :delta WHERE id = :id AND stock + :delta >= 0")
                .param("delta", delta)
                .param("id", id)
                .update();
    }

    public boolean decrementStock(long productId, int qty) {
        return updateStock(productId, -qty) > 0;
    }

    public void restoreStock(long productId, int qty) {
        updateStock(productId, qty);
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM product WHERE id = :id")
                .param("id", id)
                .update();
    }
}
