package edu.itc.shop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.web.dto.LineDto;
import edu.itc.shop.web.dto.OrderDto;

@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<OrderDto> findWithLinesByCustomerEmail(String email) {
        return orders.findWithLinesByCustomerEmail(email).stream()
                .map(order -> new OrderDto(
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
                        order.total()))
                .toList();
    }
}
