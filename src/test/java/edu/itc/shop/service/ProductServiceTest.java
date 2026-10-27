package edu.itc.shop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import edu.itc.shop.repository.ProductRepository;

@SpringBootTest(properties = {
        "logging.level.org.springframework.jdbc=DEBUG",
        "logging.level.org.springframework.transaction=DEBUG"
})
class ProductServiceTest {
    @Autowired ProductService service;
    @Autowired ProductRepository repo;

    @Test
    void restockRollsBackWhenSourceHasTooLittleStock() {
        int before1 = repo.findById(1).orElseThrow().stock();
        int before2 = repo.findById(2).orElseThrow().stock();

        assertThrows(IllegalStateException.class, () -> service.restock(1, 2, 1_000));

        assertEquals(before1, repo.findById(1).orElseThrow().stock());
        assertEquals(before2, repo.findById(2).orElseThrow().stock());   // unchanged thanks to rollback
    }

    @Test
    void restockRollsBackFirstUpdateWhenTargetDoesNotExist() {
        int before1 = repo.findById(1).orElseThrow().stock();

        // the first UPDATE succeeds, the second one hits no row and throws
        assertThrows(IllegalStateException.class, () -> service.restock(1, 999, 5));

        assertEquals(before1, repo.findById(1).orElseThrow().stock());   // first update was undone
    }

    @Test
    void restockMovesStockWhenEnoughUnits() {
        int before3 = repo.findById(3).orElseThrow().stock();
        int before4 = repo.findById(4).orElseThrow().stock();

        service.restock(3, 4, 5);
        service.restock(4, 3, 5);   // move it back so other tests see the seed data

        assertEquals(before3, repo.findById(3).orElseThrow().stock());
        assertEquals(before4, repo.findById(4).orElseThrow().stock());
    }
}
