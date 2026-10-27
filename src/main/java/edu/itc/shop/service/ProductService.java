package edu.itc.shop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.web.dto.CreateProduct;
import edu.itc.shop.web.dto.ProductDto;

@Service
public class ProductService {

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
        return findById(id);
    }

    @Transactional
    public ProductDto adjustStock(long id, int delta) {
        Product product = load(id);
        if (repository.updateStock(id, delta) == 0) {
            throw new InsufficientStockException(id, product.stock(), delta);
        }
        return findById(id);
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
