package edu.itc.shop.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe: one instance is shared by all request threads. */
public class InMemoryReviewRepository implements ReviewRepository {
    private final ConcurrentMap<Long, Review> reviews = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public List<Review> findByProductId(long productId) {
        return reviews.values().stream()
                .filter(r -> r.productId() == productId)
                .sorted(Comparator.comparingLong(Review::id))
                .toList();
    }

    @Override
    public Optional<Review> findById(long productId, long reviewId) {
        return Optional.ofNullable(reviews.get(reviewId))
                .filter(r -> r.productId() == productId);
    }

    @Override
    public Review save(Review review) {
        long id = review.id() == 0 ? ids.incrementAndGet() : review.id();
        Review saved = new Review(id, review.productId(), review.stars(), review.author(), review.text());
        reviews.put(id, saved);
        return saved;
    }
}
