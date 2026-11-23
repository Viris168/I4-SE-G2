package edu.itc.shop.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /products/{id}/reviews}. */
public record CreateReview(
        @Min(1) @Max(5) int stars,
        @NotBlank String author,
        @Size(max = 500) String text) {
}
