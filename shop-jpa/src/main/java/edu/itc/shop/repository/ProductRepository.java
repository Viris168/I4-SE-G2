package edu.itc.shop.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Product;
import edu.itc.shop.web.dto.ProductView;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    List<Product> findTop3ByCategoryOrderByPriceAmountDesc(Category category);

    List<Product> findByTagsName(String name);

    @Query("""
            select new edu.itc.shop.web.dto.ProductView(p.id, p.sku, p.name, p.price.amount, p.stock)
            from Product p
            where p.category = :category
            """)
    Page<ProductView> findByCategory(@Param("category") Category category, Pageable pageable);

    @Query(value = "SELECT p.* FROM product p WHERE p.stock < :min ORDER BY p.stock", nativeQuery = true)
    List<Product> findLowStock(@Param("min") int min);
}
