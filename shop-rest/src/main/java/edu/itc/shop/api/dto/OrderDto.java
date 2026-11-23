package edu.itc.shop.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;

/**
 * An order as the client sees it. {@code _links} names what the client may do next: {@code self} always,
 * {@code pay} (PUT) and {@code cancel} (DELETE) only while the order is NEW, and one
 * {@code product-<id>} per line.
 */
@JsonbPropertyOrder({"id", "status", "total", "lines", "links"})
public record OrderDto(String id, String status, BigDecimal total, List<PlaceOrder.Line> lines,
                       @JsonbProperty("_links") Map<String, String> links) { }
