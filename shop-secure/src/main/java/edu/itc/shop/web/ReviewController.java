package edu.itc.shop.web;

import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Review;
import edu.itc.shop.service.ProductService;
import edu.itc.shop.service.ReviewService;
import edu.itc.shop.web.dto.ReviewRequest;
import edu.itc.shop.web.dto.ReviewResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

/** JSON review endpoints under /api/** and a Thymeleaf HTML page under /products/{id}/reviews.html. */
@Controller
public class ReviewController {

    private final ReviewService reviews;
    private final ProductService products;

    public ReviewController(ReviewService reviews, ProductService products) {
        this.reviews = reviews;
        this.products = products;
    }

    @PostMapping("/api/reviews")
    @ResponseBody
    public ResponseEntity<ReviewResponse> create(@RequestBody @Valid ReviewRequest req, Authentication auth) {
        Review saved = reviews.create(auth.getName(), req);
        URI location = URI.create("/api/products/" + saved.getProductId() + "/reviews");
        return ResponseEntity.created(location).body(ReviewResponse.from(saved));
    }

    @GetMapping("/api/products/{id}/reviews")
    @ResponseBody
    public List<ReviewResponse> list(@PathVariable long id) {
        return reviews.forProduct(id).stream().map(ReviewResponse::from).toList();
    }

    @GetMapping(value = "/products/{id}/reviews.html", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable long id, Model model) {
        Product product = products.get(id);
        model.addAttribute("product", product);
        model.addAttribute("reviews", reviews.forProduct(id));
        return "reviews";
    }
}
