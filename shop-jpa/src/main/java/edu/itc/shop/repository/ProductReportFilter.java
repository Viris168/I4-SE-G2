package edu.itc.shop.repository;

import java.math.BigDecimal;

import edu.itc.shop.domain.Category;

// Challenge: every field is optional; null (or blank) means "do not filter"
public record ProductReportFilter(String q, Category category, BigDecimal maxPrice, Integer minStock, String tag) {
}
