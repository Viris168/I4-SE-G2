package edu.itc.shop.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.web.dto.CreateProduct;
import edu.itc.shop.web.dto.ProductDto;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProductDto> search(String q) {
        List<Product> products = (q == null || q.isBlank())
                ? repository.findAll()
                : repository.findByNameContaining(q);
        return products.stream().map(ProductDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductDto findById(long id) {
        return ProductDto.from(load(id));
    }

    @Transactional
    public ProductDto create(CreateProduct cmd) {
        long id = repository.insert(new Product(null, cmd.name(), cmd.price(), cmd.stock()));
        log.info("created product {} '{}'", id, cmd.name());
        return findById(id);
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name) {
        return repository.existsByName(name);
    }

    @Transactional
    public ProductDto adjustStock(long id, int delta) {
        Product product = load(id);
        if (repository.updateStock(id, delta) == 0) {
            throw new InsufficientStockException(id, product.stock(), delta);
        }
        return findById(id);
    }

    /** Moves {@code qty} units from one product to another; both updates commit or neither does. */
    @Transactional
    public void restock(long fromId, long toId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive, was " + qty);
        }
        if (repository.updateStock(fromId, -qty) == 0) {
            throw new IllegalStateException("Product " + fromId + " does not exist or has fewer than " + qty + " units");
        }
        if (repository.updateStock(toId, qty) == 0) {
            throw new IllegalStateException("Product " + toId + " does not exist");
        }
    }

    @Transactional
    public void delete(long id) {
        if (repository.delete(id) == 0) {
            throw new ProductNotFoundException(id);
        }
    }

    private Product load(long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
