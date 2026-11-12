package edu.itc.shop.web.dto;

import edu.itc.shop.domain.Money;

public record LineDto(String sku, String productName, int quantity, Money unitPrice) {
}
