package edu.itc.shop.web.dto;

import java.time.Instant;

import edu.itc.shop.domain.OrderStatus;

public final class OrderSummaryRow implements OrderSummary {

    private final Long id;
    private final OrderStatus status;
    private final Instant placedAt;
    private final CustomerNameRow customer;

    public OrderSummaryRow(Long id, OrderStatus status, Instant placedAt, String customerName) {
        this.id = id;
        this.status = status;
        this.placedAt = placedAt;
        this.customer = new CustomerNameRow(customerName);
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public OrderStatus getStatus() {
        return status;
    }

    @Override
    public Instant getPlacedAt() {
        return placedAt;
    }

    @Override
    public CustomerName getCustomer() {
        return customer;
    }

    public static final class CustomerNameRow implements OrderSummary.CustomerName {

        private final String name;

        public CustomerNameRow(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
