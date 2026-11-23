package edu.itc.shop.domain;

import java.math.BigDecimal;

/**
 * Search criteria for the product list, free of HTTP types. Null bounds mean "no bound".
 * {@code sortField} must be one of {@link ProductService#SORT_FIELDS}.
 */
public record ProductQuery(String text, BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly,
                           String sortField, boolean descending) { }
