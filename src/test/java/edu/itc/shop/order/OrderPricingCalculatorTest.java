package edu.itc.shop.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPricingCalculatorTest {

    private final OrderPricingCalculator calculator = new OrderPricingCalculator();

    @Test
    void shouldCalculateLineTotalCorrectly() {
        BigDecimal lineTotal = calculator.calculateLineTotal(new BigDecimal("25.00"), 3);
        assertThat(lineTotal).isEqualByComparingTo("75.00");
    }

    @Test
    void shouldThrowWhenUnitPriceIsNull() {
        assertThatThrownBy(() -> calculator.calculateLineTotal(null, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCalculateOrderTotalWithTax() {
        var lines = List.of(
                new OrderPricingCalculator.PricedLine(1L, 2, new BigDecimal("100.00"), new BigDecimal("200.00")),
                new OrderPricingCalculator.PricedLine(2L, 1, new BigDecimal("50.00"), new BigDecimal("50.00"))
        );

        // Subtotal = 250.00, Tax = 25.00 (10%), Total = 275.00
        var result = calculator.calculateOrderTotal(lines, new BigDecimal("0.10"));

        assertThat(result.subtotal()).isEqualByComparingTo("250.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("25.00");
        assertThat(result.total()).isEqualByComparingTo("275.00");
    }

    @Test
    void shouldCalculateOrderTotalWithZeroTax() {
        var lines = List.of(
                new OrderPricingCalculator.PricedLine(1L, 1, new BigDecimal("99.99"), new BigDecimal("99.99"))
        );

        var result = calculator.calculateOrderTotal(lines, BigDecimal.ZERO);

        assertThat(result.subtotal()).isEqualByComparingTo("99.99");
        assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(result.total()).isEqualByComparingTo("99.99");
    }
}
