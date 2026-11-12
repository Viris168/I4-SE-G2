package edu.itc.shop.domain;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public record Money(
        @Column(name = "amount", nullable = false, precision = 12, scale = 2) BigDecimal amount,
        @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "currency", nullable = false, length = 3) String currency) {

    public static Money eur(String value) {
        return new Money(new BigDecimal(value), "EUR");
    }

    public Money times(int qty) {
        return new Money(amount.multiply(BigDecimal.valueOf(qty)), currency);
    }
}
