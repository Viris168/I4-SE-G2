package edu.itc.shop.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/** An amount in a currency, written as {@code "12.50 EUR"}. */
public record Money(BigDecimal amount, String currency) {
    private static final Pattern FORMAT = Pattern.compile("(-?\\d+(?:\\.\\d+)?) ([A-Z]{3})");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    /** @throws IllegalArgumentException unless the text looks like {@code "12.50 EUR"} */
    public static Money parse(String text) {
        var m = text == null ? null : FORMAT.matcher(text.strip());
        if (m == null || !m.matches()) {
            throw new IllegalArgumentException("Cannot parse Money from '" + text + "'");
        }
        return new Money(new BigDecimal(m.group(1)), m.group(2));
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
