package edu.itc.shop.web.dto;

import java.util.List;

// Challenge: infinite-scroll envelope for GET /api/orders/feed
public record FeedResponse<T>(List<T> items, String nextCursor, boolean hasNext) {
}
