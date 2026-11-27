package edu.itc.shop.service;

import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.web.dto.ProductRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    public List<Product> all() {
        return products.findAll();
    }

    public Product get(long id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product create(ProductRequest req) {
        return products.save(new Product(req.name(), req.price(), req.stock()));
    }

    @Transactional
    public void delete(long id) {
        if (!products.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        products.deleteById(id);
    }
}
