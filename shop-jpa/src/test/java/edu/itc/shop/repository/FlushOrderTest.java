package edu.itc.shop.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import edu.itc.shop.domain.Tag;

/**
 * Task 4: Hibernate's flush order. At flush time it runs every INSERT before any DELETE,
 * whatever order the code called them in. Each test is one @Transactional method (from @DataJpaTest).
 */
@DataJpaTest
class FlushOrderTest {

    @Autowired
    private TagRepository tags;

    @Test
    void insertRunsBeforeDeleteSoReusingTheUniqueNameFails() {
        Tag sale = tags.saveAndFlush(new Tag("sale"));

        tags.delete(sale);
        tags.save(new Tag("sale"));

        assertThatThrownBy(tags::flush).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flushingTheDeleteFirstFixesIt() {
        Tag sale = tags.saveAndFlush(new Tag("sale"));

        tags.delete(sale);
        tags.flush(); // sends the DELETE now, before the new row exists
        tags.save(new Tag("sale"));
        tags.flush();

        assertThat(tags.findAll()).extracting(Tag::getName).containsOnlyOnce("sale");
    }
}
