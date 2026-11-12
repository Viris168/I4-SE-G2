package edu.itc.shop.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderLine;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.repository.OrderRepository;

// Task 5: the same report four ways, to compare statement counts (see NPlusOneTest)
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final OrderRepository orders;

    public ReportService(OrderRepository orders) {
        this.orders = orders;
    }

    /** Naive: 1 query for the orders + 1 lazy load of lines per order. */
    public Map<Long, Integer> linesPerPaidOrder() {
        return lineCounts(orders.findByStatus(OrderStatus.PAID), Order::getLines);
    }

    /** Fix A: join fetch. */
    public Map<Long, Integer> linesPerPaidOrderJoinFetch() {
        return lineCounts(orders.findByStatusWithLines(OrderStatus.PAID), Order::getLines);
    }

    /** Fix B: entity graph (lines and lines.product). */
    public Map<Long, Integer> linesPerPaidOrderEntityGraph() {
        return lineCounts(orders.findWithGraphByStatus(OrderStatus.PAID), Order::getLines);
    }

    /** Fix C: naive code, but over the @BatchSize(16) collection. */
    public Map<Long, Integer> linesPerPaidOrderBatched() {
        return lineCounts(orders.findByStatus(OrderStatus.PAID), Order::getBatchedLines);
    }

    // Step 4: the same variants, also touching line.getProduct().getName()

    public Map<Long, List<String>> productNamesPerPaidOrder() {
        return productNames(orders.findByStatus(OrderStatus.PAID), Order::getLines);
    }

    public Map<Long, List<String>> productNamesPerPaidOrderJoinFetch() {
        return productNames(orders.findByStatusWithLines(OrderStatus.PAID), Order::getLines);
    }

    public Map<Long, List<String>> productNamesPerPaidOrderEntityGraph() {
        return productNames(orders.findWithGraphByStatus(OrderStatus.PAID), Order::getLines);
    }

    public Map<Long, List<String>> productNamesPerPaidOrderBatched() {
        return productNames(orders.findByStatus(OrderStatus.PAID), Order::getBatchedLines);
    }

    private static Map<Long, Integer> lineCounts(List<Order> list, Function<Order, List<OrderLine>> lines) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Order o : list) {
            result.put(o.getId(), lines.apply(o).size());
        }
        return result;
    }

    private static Map<Long, List<String>> productNames(List<Order> list, Function<Order, List<OrderLine>> lines) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Order o : list) {
            result.put(o.getId(), lines.apply(o).stream().map(l -> l.getProduct().getName()).toList());
        }
        return result;
    }
}
