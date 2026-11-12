package edu.itc.shop.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Tag;
import edu.itc.shop.web.dto.ProductReportRow;

// Challenge, requirement 1. Rolled back after each test; names start with "Zeta" so rows that other
// test classes committed to the shared H2 database never match.
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProductReportTest {

    @Autowired ProductRepository products;
    @Autowired TagRepository tags;

    @BeforeEach
    void seed() {
        Tag sale = tags.save(new Tag("zsale"));
        Tag gaming = tags.save(new Tag("zgaming"));
        save("ZT-KB7", "Zeta mechanical keyboard", "129.00", Category.HARDWARE, 4, sale, gaming);
        save("ZT-KB1", "Zeta compact keyboard", "49.00", Category.HARDWARE, 9, sale);
        save("ZT-MO1", "Zeta mouse", "25.00", Category.HARDWARE, 0, sale, gaming);
        save("ZT-BK1", "Zeta keyboard book", "35.00", Category.BOOKS, 3);
    }

    @Test
    void tagFilterJoinsTagsButTagCountCountsAllTags() {
        var rows = products.report(new ProductReportFilter("zeta", Category.HARDWARE, null, 1, "zsale"), "amount,desc");

        assertThat(rows).extracting(ProductReportRow::sku).containsExactly("ZT-KB7", "ZT-KB1");
        assertThat(rows.get(0)).isEqualTo(new ProductReportRow(
                "ZT-KB7", "Zeta mechanical keyboard", new BigDecimal("129.00"), 4, 2));
    }

    @Test
    void filtersAreOptionalAndCombinable() {
        assertThat(products.report(new ProductReportFilter("zeta", null, null, null, null), "sku"))
                .extracting(ProductReportRow::sku).containsExactly("ZT-BK1", "ZT-KB1", "ZT-KB7", "ZT-MO1");
        assertThat(products.report(new ProductReportFilter("zeta keyboard", null, new BigDecimal("50"), null, null), "sku"))
                .extracting(ProductReportRow::sku).containsExactly("ZT-BK1");
        assertThat(products.report(new ProductReportFilter("zeta", null, null, null, null), "tagCount,desc"))
                .extracting(ProductReportRow::tagCount).containsExactly(2, 2, 1, 0);
    }

    @Test
    void sortIsWhitelisted() {
        assertThatThrownBy(() -> products.report(new ProductReportFilter(null, null, null, null, null), "id; drop table product"))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining("cannot sort by");
    }

    private void save(String sku, String name, String price, Category category, int stock, Tag... productTags) {
        Product p = new Product(sku, name, Money.eur(price), category, stock);
        for (Tag t : productTags) {
            p.tag(t);
        }
        products.save(p);
    }
}
