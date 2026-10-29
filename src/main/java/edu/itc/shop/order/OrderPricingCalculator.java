package edu.itc.shop.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class OrderPricingCalculator {

    public record PricedLine(Long productId, int qty, BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record PriceCalculationResult(
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total
    ) {}

    /**
     * Computes the line total: unitPrice * qty
     */
    public BigDecimal calculateLineTotal(BigDecimal unitPrice, int qty) {
        if (unitPrice == null || qty < 0) {
            throw new IllegalArgumentException("Invalid price or quantity");
        }
        return unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes order subtotal, tax and grand total with taxRate.
     * total = subtotal + (subtotal * taxRate)
     */
    public PriceCalculationResult calculateOrderTotal(List<PricedLine> lines, BigDecimal taxRate) {
        BigDecimal subtotal = lines.stream()
                .map(PricedLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal effectiveTaxRate = taxRate != null ? taxRate : BigDecimal.ZERO;
        BigDecimal taxAmount = subtotal.multiply(effectiveTaxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        return new PriceCalculationResult(subtotal, taxAmount, total);
    }
}
