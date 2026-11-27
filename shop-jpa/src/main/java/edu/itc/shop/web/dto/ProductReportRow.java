package edu.itc.shop.web.dto;

import java.math.BigDecimal;

// Challenge: one row of GET /api/reports/products
public record ProductReportRow(String sku, String name, BigDecimal amount, int stock, int tagCount) {
}
