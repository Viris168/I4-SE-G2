package edu.itc.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An order placed by a customer. {@code customer} holds the owner's username;
 * it is the value the owner checks in OrderService compare with authentication.name.
 */
@Entity
@Table(name = "orders")   // ORDER is a reserved word in SQL
public class Order {

    public enum Status { NEW, SHIPPED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customer;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;

    protected Order() { }   // for JPA

    public Order(String customer, Long productId, int quantity) {
        this.customer = customer;
        this.productId = productId;
        this.quantity = quantity;
    }

    public void ship() {
        if (status != Status.NEW) {
            throw new OrderStateException("Only NEW orders can be shipped, this one is " + status);
        }
        status = Status.SHIPPED;
    }

    public Long getId() { return id; }
    public String getCustomer() { return customer; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "Order{id=" + id + ", customer=" + customer + ", productId=" + productId
                + ", quantity=" + quantity + ", status=" + status + '}';
    }
}
