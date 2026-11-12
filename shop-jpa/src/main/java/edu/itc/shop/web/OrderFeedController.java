package edu.itc.shop.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.itc.shop.service.OrderFeedService;
import edu.itc.shop.web.dto.FeedResponse;
import edu.itc.shop.web.dto.OrderSummary;

// Challenge: GET /api/orders/feed (keyset) and /api/orders/feed/slice (offset)
@RestController
@RequestMapping("/api/orders/feed")
public class OrderFeedController {

    private final OrderFeedService feed;

    public OrderFeedController(OrderFeedService feed) {
        this.feed = feed;
    }

    @GetMapping
    public FeedResponse<OrderSummary> keyset(@RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String after) {
        return feed.keyset(after, size);
    }

    @GetMapping("/slice")
    public FeedResponse<OrderSummary> slice(@RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String after) {
        return feed.slice(after, size);
    }
}
