package edu.itc.shop.domain.order;

import java.math.BigDecimal;
import java.util.List;
import jakarta.ws.rs.NotFoundException;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.ProductService;

/**
 * Order rules: stock is reserved when an order is placed and given back when it is cancelled;
 * only NEW orders can be paid or cancelled. Transitions are synchronized so that two concurrent
 * requests cannot both pay, or both cancel (and restore the stock twice).
 */
public class OrderService {
    private final ProductService products;
    private final OrderRepository orders;

    public OrderService(ProductService products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    /** @throws NotFoundException for an unknown product; InsufficientStockException (409) when stock is short */
    public Order place(List<OrderLine> lines) {
        List<Product> reserved = products.reserve(lines);      // all lines or nothing
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            total = total.add(reserved.get(i).price().multiply(BigDecimal.valueOf(lines.get(i).quantity())));
        }
        return orders.save(new Order(orders.nextId(), lines, total, OrderStatus.NEW));
    }

    public Order get(String id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    public synchronized Order pay(String id) {
        Order order = get(id);
        if (order.status() != OrderStatus.NEW) throw new OrderStateException(order, "paid");
        return orders.save(order.withStatus(OrderStatus.PAID));
    }

    public synchronized Order cancel(String id) {
        Order order = get(id);
        if (order.status() != OrderStatus.NEW) throw new OrderStateException(order, "cancelled");
        products.release(order.lines());
        return orders.save(order.withStatus(OrderStatus.CANCELLED));
    }
}
