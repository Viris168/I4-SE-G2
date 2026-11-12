package edu.itc.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class EntityIdentityTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void entityStaysInHashSetAfterPersistAssignsId() {
        Product product = new Product("KB-1", "Keyboard", Money.eur("49.00"), Category.HARDWARE, 10);
        Set<Product> set = new HashSet<>();
        set.add(product);
        assertThat(product.getId()).isNull();

        em.persist(product);
        em.flush();

        assertThat(product.getId()).isNotNull();
        assertThat(set.contains(product)).isTrue();
    }

    @Test
    void reloadedInstanceIsEqualButNotTheSameObject() {
        Product original = em.persistFlushFind(
                new Product("MO-1", "Mouse", Money.eur("20.00"), Category.HARDWARE, 5));
        em.clear();

        Product reloaded = em.find(Product.class, original.getId());

        assertThat(reloaded).isNotSameAs(original);
        assertThat(reloaded).isEqualTo(original);
        assertThat(reloaded.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    void twoNewEntitiesWithoutIdAreNotEqual() {
        Product a = new Product("A-1", "A", Money.eur("1.00"), Category.TOYS, 1);
        Product b = new Product("B-1", "B", Money.eur("1.00"), Category.TOYS, 1);

        assertThat(a).isNotEqualTo(b);
        assertThat(a).isEqualTo(a);
    }
}
