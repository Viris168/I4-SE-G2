package edu.itc.shop.health;

import java.util.List;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import edu.itc.shop.config.ShopProperties;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;

/** Reported as {@code lowStock}: DOWN while any product is below {@code shop.low-stock-threshold}. */
@Component
public class LowStockHealthIndicator implements HealthIndicator {

    private final ProductRepository repository;
    private final ShopProperties props;

    public LowStockHealthIndicator(ProductRepository repository, ShopProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @Override
    public Health health() {
        List<String> low = repository.findByStockLessThan(props.lowStockThreshold()).stream()
                .map(Product::name)
                .toList();
        Health.Builder builder = low.isEmpty() ? Health.up() : Health.down();
        return builder
                .withDetail("threshold", props.lowStockThreshold())
                .withDetail("lowStockProducts", low)
                .build();
    }
}
