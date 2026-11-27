package edu.itc.shop.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Order;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.web.dto.FeedResponse;
import edu.itc.shop.web.dto.OrderSummary;
import edu.itc.shop.web.dto.OrderSummaryRow;

// Challenge: the same newest-first order feed, implemented twice
@Service
@Transactional(readOnly = true)
public class OrderFeedService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id"));

    private final OrderRepository orders;

    public OrderFeedService(OrderRepository orders) {
        this.orders = orders;
    }

    /** Keyset: the cursor is "placedAt,id" of the last item; the next page starts strictly after it. */
    public FeedResponse<OrderSummary> keyset(String after, int size) {
        ScrollPosition position = after == null || after.isBlank() ? ScrollPosition.keyset() : parse(after);
        Window<Order> window = orders.findAllByOrderByPlacedAtDescIdDesc(position, Limit.of(size));

        String next = null;
        if (window.hasNext() && !window.isEmpty()) {
            Map<String, Object> keys = ((KeysetScrollPosition) window.positionAt(window.size() - 1)).getKeys();
            next = keys.get("placedAt") + "," + keys.get("id");
        }
        return new FeedResponse<>(window.map(OrderFeedService::summary).getContent(), next, window.hasNext());
    }

    /** Offset: the cursor is the next page number; page n skips n * size rows. */
    public FeedResponse<OrderSummary> slice(String after, int size) {
        int page = after == null || after.isBlank() ? 0 : Integer.parseInt(after);
        Slice<OrderSummary> slice = orders.feedSlice(PageRequest.of(page, size, NEWEST_FIRST));
        return new FeedResponse<>(slice.getContent(),
                slice.hasNext() ? String.valueOf(page + 1) : null, slice.hasNext());
    }

    /** Keyset with a row-value predicate, so the database can seek instead of scan (see README). */
    public FeedResponse<OrderSummary> seek(String after, int size) {
        java.util.List<OrderSummary> rows;
        if (after == null || after.isBlank()) {
            rows = orders.feedSlice(PageRequest.of(0, size + 1, NEWEST_FIRST)).getContent();
        } else {
            Map<String, ?> keys = ((KeysetScrollPosition) parse(after)).getKeys();
            rows = orders.feedAfter((Instant) keys.get("placedAt"), (Long) keys.get("id"), Limit.of(size + 1));
        }
        boolean hasNext = rows.size() > size;
        java.util.List<OrderSummary> items = hasNext ? rows.subList(0, size) : rows;
        String next = hasNext ? items.get(size - 1).getPlacedAt() + "," + items.get(size - 1).getId() : null;
        return new FeedResponse<>(items, next, hasNext);
    }

    private static ScrollPosition parse(String cursor) {
        int comma = cursor.lastIndexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("cursor must be <placedAt>,<id>");
        }
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("placedAt", Instant.parse(cursor.substring(0, comma)));
        keys.put("id", Long.valueOf(cursor.substring(comma + 1)));
        return ScrollPosition.forward(keys);
    }

    private static OrderSummary summary(Order o) {
        return new OrderSummaryRow(o.getId(), o.getStatus(), o.getPlacedAt(), o.getCustomer().getName());
    }
}
