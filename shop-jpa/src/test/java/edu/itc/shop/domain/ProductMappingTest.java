package edu.itc.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProductMappingTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void persistsAndReloadsProductWithMoneyAndCategory() {
        Product product = new Product("HW-001", "Mechanical Keyboard", Money.eur("129.99"), Category.HARDWARE, 10);
        em.persist(product);
        em.flush();
        em.clear();

        Product reloaded = em.find(Product.class, product.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getSku()).isEqualTo("HW-001");
        assertThat(reloaded.getName()).isEqualTo("Mechanical Keyboard");
        assertThat(reloaded.getPrice().amount()).isEqualByComparingTo(new BigDecimal("129.99"));
        assertThat(reloaded.getPrice().currency()).isEqualTo("EUR");
        assertThat(reloaded.getPrice().times(2).amount()).isEqualByComparingTo(new BigDecimal("259.98"));
        assertThat(reloaded.getCategory()).isEqualTo(Category.HARDWARE);
        assertThat(reloaded.getStock()).isEqualTo(10);
    }

    @Test
    void storesCategoryAsStringNotOrdinal() {
        Product product = new Product("HW-002", "USB-C Hub", Money.eur("49.50"), Category.HARDWARE, 5);
        em.persistAndFlush(product);

        Object rawCategory = em.getEntityManager()
                .createNativeQuery("SELECT category FROM product WHERE id = :id")
                .setParameter("id", product.getId())
                .getSingleResult();

        assertThat(rawCategory).isEqualTo("HARDWARE");
    }
}
