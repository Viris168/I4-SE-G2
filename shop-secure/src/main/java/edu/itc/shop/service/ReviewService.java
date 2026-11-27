package edu.itc.shop.service;

import edu.itc.shop.domain.Review;
import edu.itc.shop.repository.ReviewRepository;
import edu.itc.shop.web.dto.ReviewRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final ProductService products;

    public ReviewService(ReviewRepository reviews, ProductService products) {
        this.reviews = reviews;
        this.products = products;
    }

    @Transactional
    public Review create(String author, ReviewRequest req) {
        products.get(req.productId()); // 404 if product does not exist
        return reviews.save(new Review(req.productId(), author, req.stars(), req.text()));
    }

    @Transactional(readOnly = true)
    public List<Review> forProduct(long productId) {
        products.get(productId);
        return reviews.findByProductIdOrderByIdAsc(productId);
    }
}
