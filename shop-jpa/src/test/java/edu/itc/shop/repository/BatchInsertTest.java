package edu.itc.shop.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;
import edu.itc.shop.support.QueryCounter;

// Task 5, step 5: hibernate.jdbc.batch_size=50 turns 1 000 INSERTs into 20 JDBC batches
@SpringBootTest
class BatchInsertTest {

    @Autowired ProductRepository products;
    @Autowired QueryCounter counter;
    @Autowired TransactionTemplate tx;

    private final String prefix = "B" + UUID.randomUUID().toString().substring(0, 6) + "-";

    @Test
    void saveAllOf1000ProductsIsBatched() {
        List<Product> batch = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            batch.add(new Product(prefix + i, "Batch product " + i, Money.eur("1.00"), Category.TOYS, 1));
        }

        counter.reset();
        tx.executeWithoutResult(status -> products.saveAll(batch));

        System.out.printf("BatchInsertTest: inserts=%d, prepared statements=%d%n", counter.inserts(), counter.count());
        assertThat(counter.inserts()).isEqualTo(1000);
        // 20 sequence calls (allocationSize 50) + 20 insert batches (batch_size 50)
        assertThat(counter.count()).isLessThan(100);
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> products.deleteAllInBatch(
                products.findAll().stream().filter(p -> p.getSku().startsWith(prefix)).toList()));
    }
}
