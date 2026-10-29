package edu.itc.shop.order;

import edu.itc.shop.aspect.Audited;
import edu.itc.shop.config.ShopProperties;
import edu.itc.shop.customer.CustomerRepository;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.service.InsufficientStockException;
import edu.itc.shop.service.Notifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final ShopProperties props;
    private final Notifier notifier;

    public OrderService(
            OrderRepository orders,
            ProductRepository products,
            CustomerRepository customers,
            ShopProperties props,
            Notifier notifier
    ) {
        this.orders = orders;
        this.products = products;
        this.customers = customers;
        this.props = props;
        this.notifier = notifier;
    }

    /**
     * Requirement: Place order atomically inside @Transactional.
     * 1. validate no duplicate productId
     * 2. for each line: products.updateStock(id, -qty) == 0 -> throw InsufficientStockException
     * 3. load unit prices, compute total (net * (1 + taxRate)), round HALF_UP to 2 decimals
     * 4. orders.insert(order, lines) -> id
     * 5. return OrderDto
     */
    @Transactional
    @Audited
    public OrderDto place(PlaceOrder cmd) {
        // 1. validate no duplicate productId
        if (cmd.hasDuplicateProducts()) {
            throw new OrderConflictException("Duplicate productId in order lines");
        }

        var customer = customers.findById(cmd.customerId())
                .orElseThrow(() -> new OrderConflictException("Customer not found: " + cmd.customerId()));

        // 2. for each line: products.updateStock(id, -qty) == 0 -> throw InsufficientStockException
        // 3. load unit prices, compute total (net * (1 + taxRate)), round HALF_UP to 2 decimals
        BigDecimal net = BigDecimal.ZERO;
        List<OrderLine> orderLines = new ArrayList<>();

        for (PlaceOrder.Line line : cmd.lines()) {
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> new OrderConflictException("Product not found: " + line.productId()));

            if (products.updateStock(line.productId(), -line.qty()) == 0) {
                throw new InsufficientStockException(line.productId(), product.stock(), -line.qty());
            }

            BigDecimal lineNet = product.price().multiply(BigDecimal.valueOf(line.qty()));
            net = net.add(lineNet);
            orderLines.add(new OrderLine(null, null, product.id(), line.qty(), product.price()));
        }

        BigDecimal taxMultiplier = BigDecimal.ONE.add(props.taxRate());
        BigDecimal total = net.multiply(taxMultiplier).setScale(2, RoundingMode.HALF_UP);

        // 4. orders.insert(order, lines) -> id
        Long orderId = orders.insert(customer.id(), OrderStatus.NEW, total, orderLines);

        Order order = orders.findOrderById(orderId)
                .orElseThrow(() -> new IllegalStateException("Failed to find created order: " + orderId));

        // 5. return OrderDto
        return toDto(order);
    }

    /**
     * Requirement: Cancel only NEW orders, restores stock, notifier.send(...)
     */
    @Transactional
    @Audited
    public OrderDto cancel(long id) {
        Order order = orders.findOrderById(id)
                .orElseThrow(() -> new OrderConflictException("Order not found: " + id));

        // only NEW -> CANCELLED, restore stock, notifier.send(...)
        if (order.status() != OrderStatus.NEW) {
            throw new OrderConflictException("Only NEW orders can be cancelled. Current status: " + order.status());
        }

        for (OrderLine line : order.lines()) {
            products.updateStock(line.productId(), line.qty());
        }

        orders.updateStatus(id, OrderStatus.CANCELLED);

        String recipient = customers.findById(order.customerId())
                .map(c -> c.email())
                .orElse("customer@example.com");
        notifier.send(recipient, "Order #" + id + " has been cancelled.");

        return get(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> list(Long customerId, OrderStatus status, int page, int size) {
        List<Order> orderList = orders.findOrders(customerId, status, page, size);
        long totalElements = orders.countOrders(customerId, status);

        List<OrderDto> dtos = orderList.stream()
                .map(this::toDto)
                .toList();

        return new Page<>(dtos, page, size, totalElements);
    }

    @Transactional(readOnly = true)
    public Optional<OrderDto> get(long id) {
        return orders.findOrderById(id).map(this::toDto);
    }

    private OrderDto toDto(Order o) {
        List<OrderDto.OrderLineDto> lines = o.lines().stream()
                .map(l -> new OrderDto.OrderLineDto(l.productId(), l.qty(), l.unitPrice()))
                .toList();
        return new OrderDto(o.id(), o.customerId(), o.status(), o.placedAt(), o.total(), lines);
    }
}
