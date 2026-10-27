package edu.itc.shop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import edu.itc.shop.config.ShopProperties;

class PricingServiceTest {

    private final PricingService pricing = new PricingService(new ShopProperties("USD", new BigDecimal("0.10"), 5));

    @Test
    void grossAddsTaxAndRoundsHalfUp() {
        assertEquals(new BigDecimal("54.89"), pricing.gross(new BigDecimal("49.90")));
        assertEquals(new BigDecimal("0.06"), pricing.gross(new BigDecimal("0.05")));   // 0.055 → 0.06
    }
}
