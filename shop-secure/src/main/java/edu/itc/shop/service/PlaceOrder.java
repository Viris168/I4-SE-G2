package edu.itc.shop.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body of POST /api/orders. Deliberately has no customer field:
 * the customer always comes from the Authentication.
 */
public record PlaceOrder(
        @NotNull Long productId,
        @Min(1) @Max(100) int quantity) {}
