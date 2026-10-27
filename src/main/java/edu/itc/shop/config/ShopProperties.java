package edu.itc.shop.config;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed {@code shop.*} settings; the application refuses to start if they are invalid. */
@Validated
@ConfigurationProperties("shop")
public record ShopProperties(
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal taxRate,
        @PositiveOrZero int lowStockThreshold) {
}
