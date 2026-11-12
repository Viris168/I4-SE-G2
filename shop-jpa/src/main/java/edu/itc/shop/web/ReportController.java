package edu.itc.shop.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.itc.shop.domain.Category;
import edu.itc.shop.repository.ProductReportFilter;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.web.dto.ProductReportRow;

// Challenge
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ProductRepository products;

    public ReportController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping("/products")
    public List<ProductReportRow> products(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Category category,
                                           @RequestParam(required = false) BigDecimal maxPrice,
                                           @RequestParam(required = false) Integer minStock,
                                           @RequestParam(required = false) String tag,
                                           @RequestParam(required = false) String sort) {
        return products.report(new ProductReportFilter(q, category, maxPrice, minStock, tag), sort);
    }
}
