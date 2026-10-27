package edu.itc.shop.web.dto;

import jakarta.validation.constraints.NotNull;

public record StockChange(@NotNull Integer delta) {
}
