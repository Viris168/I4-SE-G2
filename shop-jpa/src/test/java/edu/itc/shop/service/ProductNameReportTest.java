package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import edu.itc.shop.support.QueryCounter;
import edu.itc.shop.support.TestData;

// Task 5, step 4: touching line.getProduct().getName() brings N+1 back for every variant
// that did not fetch the product. 50 orders reference all 30 products.
class ProductNameReportTest {

    abstract static class Base {
        @Autowired ReportService report;
        @Autowired QueryCounter counter;
        @Autowired TestData data;

        @BeforeEach
        void seed() { data.paidOrders(50, 3); }

        long statements(Supplier<Map<Long, ?>> variant) {
            counter.reset();
            assertThat(variant.get()).hasSize(50);
            return counter.count();
        }
    }

    @Nested
    @SpringBootTest
    @Tag("demo")
    class WithoutDefaultBatchFetchSize extends Base {
        @Test
        void productNamesRegressIntoNPlusOne() {
            assertThat(statements(report::productNamesPerPaidOrder)).isEqualTo(1 + 50 + 30);
            assertThat(statements(report::productNamesPerPaidOrderJoinFetch)).isEqualTo(1 + 30);
            assertThat(statements(report::productNamesPerPaidOrderEntityGraph)).isEqualTo(1);
            assertThat(statements(report::productNamesPerPaidOrderBatched)).isEqualTo(5 + 30);
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=32")
    class WithDefaultBatchFetchSize32 extends Base {
        @Test
        void defaultBatchFetchSizeCapsTheRegression() {
            // lines: 1 + ceil(50/32) = 3, products: ceil(30/32) = 1
            assertThat(statements(report::productNamesPerPaidOrder)).isEqualTo(1 + 2 + 1);
            assertThat(statements(report::productNamesPerPaidOrderJoinFetch)).isEqualTo(1 + 1);
            assertThat(statements(report::productNamesPerPaidOrderEntityGraph)).isEqualTo(1);
            // @BatchSize(16) on batchedLines still wins over the global 32
            assertThat(statements(report::productNamesPerPaidOrderBatched)).isEqualTo(5 + 1);
        }
    }
}
