package edu.itc.shop.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// Task 5, step 7: V2 is applied on top of V1 and its indexes exist
@SpringBootTest
class FlywayMigrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void historyHasTwoMigrationsAndTheIndexesExist() {
        List<Map<String, Object>> all = jdbc.queryForList(
                "select \"installed_rank\", \"version\", \"description\", \"type\" from \"flyway_schema_history\" order by \"installed_rank\"");
        System.out.println("flyway_schema_history: " + all);
        // Flyway 11+ also writes a "<< Flyway Schema History table created >>" row (rank -1, no version)
        List<Map<String, Object>> history = all.stream().filter(row -> row.get("version") != null).toList();
        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2");
        assertThat(history.get(1).get("description")).isEqualTo("indexes");

        List<String> indexes = jdbc.queryForList(
                "select index_name from information_schema.indexes where index_name like 'IDX_%'", String.class);
        assertThat(indexes).contains("IDX_ORDERS_CUSTOMER_PLACED", "IDX_ORDER_LINE_ORDER", "IDX_PRODUCT_CATEGORY_AMOUNT");
    }
}
