package edu.itc.shop.service;

import edu.itc.shop.domain.Order;
import edu.itc.shop.repository.OrderRepository;
import java.util.List;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every authorization rule for orders lives here as an annotation;
 * the controller contains no role checks.
 */
@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    /** A user may only place orders for themselves. */
    @PreAuthorize("#customerId == authentication.name")
    @Transactional
    public Order place(String customerId, PlaceOrder cmd) {
        return orders.save(new Order(customerId, cmd.productId(), cmd.quantity()));
    }

    /** Own history, or anyone's for an admin. */
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.name")
    @Transactional(readOnly = true)
    public List<Order> history(String customerId) {
        return orders.findByCustomer(customerId);
    }

    /** Loaded first, then checked: only the owner or an admin may see the result. */
    @PostAuthorize("hasRole('ADMIN') or returnObject.customer == authentication.name")
    @Transactional(readOnly = true)
    public Order get(long id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Order ship(long id) {
        Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        order.ship();
        return order;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<Order> all() {
        return orders.findAll();
    }
}
