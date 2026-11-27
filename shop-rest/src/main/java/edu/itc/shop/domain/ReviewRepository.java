package edu.itc.shop.domain;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository {

    List<Review> findByProductId(long productId);

    Optional<Review> findById(long productId, long reviewId);

    /** Assigns an id when {@code id == 0}. */
    Review save(Review review);
}
