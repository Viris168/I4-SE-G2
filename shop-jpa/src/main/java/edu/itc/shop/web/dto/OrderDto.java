package edu.itc.shop.web.dto;

import java.time.Instant;
import java.util.List;

import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.OrderStatus;

public record OrderDto(Long id, OrderStatus status, Instant placedAt, List<LineDto> lines, Money total) {
}
