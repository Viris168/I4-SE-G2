package edu.itc.shop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.web.dto.LineDto;
import edu.itc.shop.web.dto.OrderDto;
import jakarta.persistence.EntityNotFoundException;

@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<OrderDto> findWithLinesByCustomerEmail(String email) {
        return orders.findWithLinesByCustomerEmail(email).stream()
                .map(OrderService::toDto)
                .toList();
    }

    /**
     * A managed change: the order is loaded inside the transaction, so Hibernate's dirty
     * checking writes the UPDATE at commit. There is deliberately no save() call.
     */
    @Transactional
    public void markPaid(Long orderId) {
        load(orderId).setStatus(OrderStatus.PAID);
    }

    /** Lines are loaded lazily, which works because the mapping happens inside the transaction. */
    @Transactional(readOnly = true)
    public OrderDto view(Long id) {
        return toDto(load(id));
    }

    private Order load(Long id) {
        return orders.findById(id).orElseThrow(() -> new EntityNotFoundException("Order " + id + " not found"));
    }

    private static OrderDto toDto(Order order) {
        return new OrderDto(
                order.getId(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getLines().stream()
                        .map(line -> new LineDto(
                                line.getProduct().getSku(),
                                line.getProduct().getName(),
                                line.getQuantity(),
                                line.getUnitPrice()))
                        .toList(),
                order.total());
    }
}
