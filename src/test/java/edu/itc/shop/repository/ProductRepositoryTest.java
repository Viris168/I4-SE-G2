package edu.itc.shop.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import edu.itc.shop.domain.Product;

@JdbcTest
@Import({ProductRepository.class, RawJdbcProductRepository.class})
class ProductRepositoryTest {
    @Autowired ProductRepository repo;
    @Autowired RawJdbcProductRepository rawRepo;

    @Test
    void findAllReturnsSeedData() {
        assertEquals(5, repo.findAll().size());
    }

    @Test
    void rawJdbcAndJdbcClientReturnTheSameRows() {
        assertEquals(repo.findAll(), rawRepo.findAll());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertTrue(repo.findById(999).isEmpty());
    }

    @Test
    void findByNameContainingIgnoresCase() {
        var found = repo.findByNameContaining("MOUSE");   // matches "Wireless mouse"
        assertEquals(1, found.size());
        assertTrue(found.stream().allMatch(p -> p.name().toLowerCase().contains("mouse")));
    }

    @Test
    void insertReturnsGeneratedIdAndRowCanBeRead() {
        long id = repo.insert(new Product(null, "USB-C cable", new BigDecimal("7.90"), 25));

        Product saved = repo.findById(id).orElseThrow();
        assertEquals("USB-C cable", saved.name());
        assertEquals(new BigDecimal("7.90"), saved.price());
        assertEquals(25, saved.stock());
    }

    @Test
    void updateStockRefusesToGoNegative() {
        int stock = repo.findById(2).orElseThrow().stock();

        assertEquals(0, repo.updateStock(2, -(stock + 1)));
        assertEquals(1, repo.updateStock(2, -stock));
        assertEquals(0, repo.findById(2).orElseThrow().stock());
    }

    @Test
    void deleteRemovesRow() {
        assertEquals(1, repo.delete(5));
        assertTrue(repo.findById(5).isEmpty());
        assertEquals(0, repo.delete(5));
    }

    @Test
    void duplicateNameIsTranslatedToDuplicateKeyException() {
        var duplicate = new Product(null, "Keyboard", new BigDecimal("1.00"), 1);

        assertThrows(DuplicateKeyException.class, () -> repo.insert(duplicate));
    }
}
