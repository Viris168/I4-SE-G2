package edu.itc.shop.domain;

import java.math.BigDecimal;

public record Product(long id, String name, String sku, BigDecimal price, int stock, int version) {

    public Product withId(long id) {
        return new Product(id, name, sku, price, stock, version);
    }

    public Product withVersion(int version) {
        return new Product(id, name, sku, price, stock, version);
    }

    public Product withStock(int newStock) {
        return new Product(id, name, sku, price, newStock, version);
    }
}
