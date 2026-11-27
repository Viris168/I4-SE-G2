package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import edu.itc.shop.support.QueryCounter;
import edu.itc.shop.support.TestData;

// Task 5
@SpringBootTest
@Execution(ExecutionMode.SAME_THREAD)
class NPlusOneTest {

    @Autowired ReportService report;
    @Autowired QueryCounter counter;
    @Autowired TestData data;

    @BeforeEach
    void seed() { data.paidOrders(50, 3); }      // 50 orders × 3 lines, idempotent

    @Test @Tag("demo")
    void naiveImplementationRunsNPlusOneQueries() {
        counter.reset();
        var result = report.linesPerPaidOrder();
        assertThat(counter.count()).isEqualTo(51);          // 1 + 50
        assertThat(result).hasSize(50).allSatisfy((id, lines) -> assertThat(lines).isEqualTo(3));
    }

    @Test
    void joinFetchRunsOneQuery() {
        counter.reset();
        var result = report.linesPerPaidOrderJoinFetch();
        assertThat(counter.count()).isEqualTo(1);
        assertThat(result).hasSize(50).allSatisfy((id, lines) -> assertThat(lines).isEqualTo(3));
    }

    @Test
    void entityGraphRunsOneQuery() {
        counter.reset();
        var result = report.linesPerPaidOrderEntityGraph();
        assertThat(counter.count()).isEqualTo(1);
        assertThat(result).hasSize(50).allSatisfy((id, lines) -> assertThat(lines).isEqualTo(3));
    }

    @Test
    void batchSizeRunsOnePlusCeilNOverBatch() {
        counter.reset();
        var result = report.linesPerPaidOrderBatched();
        assertThat(counter.count()).isEqualTo(1 + (int) Math.ceil(50 / 16.0));   // 5
        assertThat(result).hasSize(50).allSatisfy((id, lines) -> assertThat(lines).isEqualTo(3));
    }
}
