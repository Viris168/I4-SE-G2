package edu.itc.shop.api.dto;

import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;

/** v2 product representation served for {@code application/vnd.shop.v2+json}. */
public record ProductV2(long id, String name, String sku, Money price, int stock, boolean available) {

    public static final String MEDIA_TYPE = "application/vnd.shop.v2+json";

    public static ProductV2 from(Product p) {
        return new ProductV2(p.id(), p.name(), p.sku(), new Money(p.price(), "EUR"), p.stock(), p.stock() > 0);
    }
}
