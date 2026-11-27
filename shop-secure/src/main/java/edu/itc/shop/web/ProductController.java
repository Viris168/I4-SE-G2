package edu.itc.shop.web;

import edu.itc.shop.domain.Product;
import edu.itc.shop.service.ProductService;
import edu.itc.shop.web.dto.ProductRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<Product> all() {
        return service.all();
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody @Valid ProductRequest req) {
        Product saved = service.create(req);
        return ResponseEntity.created(URI.create("/api/products/" + saved.getId())).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
