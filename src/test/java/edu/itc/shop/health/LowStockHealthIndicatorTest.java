package edu.itc.shop.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import edu.itc.shop.config.ShopProperties;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;

class LowStockHealthIndicatorTest {

    private final ProductRepository repo = mock(ProductRepository.class);
    private final ShopProperties props = new ShopProperties("USD", new BigDecimal("0.10"), 5);
    private final LowStockHealthIndicator indicator = new LowStockHealthIndicator(repo, props);

    @Test
    void downWhenAProductIsBelowThreshold() {
        given(repo.findByStockLessThan(5)).willReturn(List.of(new Product(2L, "Keyboard", new BigDecimal("89.00"), 3)));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(List.of("Keyboard"), health.getDetails().get("lowStockProducts"));
    }

    @Test
    void upWhenEveryProductHasEnoughStock() {
        given(repo.findByStockLessThan(5)).willReturn(List.of());

        assertEquals(Status.UP, indicator.health().getStatus());
    }
}
