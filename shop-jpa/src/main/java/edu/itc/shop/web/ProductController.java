package edu.itc.shop.web;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.itc.shop.domain.Category;
import edu.itc.shop.service.ProductService;
import edu.itc.shop.web.dto.ProductView;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public Page<ProductView> list(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) Category category,
                                  @RequestParam(required = false) BigDecimal maxPrice,
                                  @RequestParam(required = false) Boolean inStock,
                                  @PageableDefault(size = 20) Pageable pageable) {
        return products.search(q, category, maxPrice, inStock, pageable);
    }
}
