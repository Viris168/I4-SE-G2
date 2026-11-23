package edu.itc.shop.domain.order;

import java.util.Optional;

public interface OrderRepository {
    /** A fresh id such as {@code o-1001}. */
    String nextId();
    Optional<Order> findById(String id);
    Order save(Order order);
}
