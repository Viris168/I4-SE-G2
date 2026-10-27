package edu.itc.shop.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import edu.itc.shop.config.ShopProperties;

@Service
public class PricingService {

    private final ShopProperties props;

    public PricingService(ShopProperties props) {
        this.props = props;
    }

    /** Net price plus tax: {@code price × (1 + taxRate)}, rounded HALF_UP to 2 decimals. */
    public BigDecimal gross(BigDecimal net) {
        return net.multiply(BigDecimal.ONE.add(props.taxRate())).setScale(2, RoundingMode.HALF_UP);
    }

    public String currency() {
        return props.currency();
    }

    public int lowStockThreshold() {
        return props.lowStockThreshold();
    }
}
