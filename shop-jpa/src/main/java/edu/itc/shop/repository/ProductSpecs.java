package edu.itc.shop.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;

public final class ProductSpecs {

    private ProductSpecs() {
    }

    public static Specification<Product> nameContains(String q) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + q.toLowerCase() + "%");
    }

    public static Specification<Product> inCategory(Category category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Product> cheaperThan(BigDecimal max) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.<Money>get("price").<BigDecimal>get("amount"), max);
    }

    public static Specification<Product> inStock() {
        return (root, query, cb) -> cb.greaterThan(root.get("stock"), 0);
    }

    public static Specification<Product> filter(String q, Category category, BigDecimal max, Boolean inStock) {
        List<Specification<Product>> specs = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            specs.add(nameContains(q));
        }
        if (category != null) {
            specs.add(inCategory(category));
        }
        if (max != null) {
            specs.add(cheaperThan(max));
        }
        if (inStock != null) {
            specs.add(inStock ? inStock() : outOfStock());
        }
        if (specs.isEmpty()) {
            return (root, query, cb) -> null;
        }
        return Specification.allOf(specs);
    }

    private static Specification<Product> outOfStock() {
        return (root, query, cb) -> cb.equal(root.get("stock"), 0);
    }
}
