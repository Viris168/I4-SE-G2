package edu.itc.shop.web.dto;

import java.math.BigDecimal;

/** One line of the JSP product table: the product plus its gross (taxed) price. */
public record ProductRow(long id, String name, BigDecimal price, BigDecimal gross, int stock) {
    public static ProductRow from(ProductDto p, BigDecimal gross) {
        return new ProductRow(p.id(), p.name(), p.price(), gross, p.stock());
    }
}
