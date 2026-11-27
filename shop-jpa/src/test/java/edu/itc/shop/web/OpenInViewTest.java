package edu.itc.shop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import edu.itc.shop.support.TestData;

// Task 5, step 6: with open-in-view=false there is no session in the controller.
// Deliberately not @Transactional: a test transaction would keep the session open and hide the bug.
@SpringBootTest
class OpenInViewTest {

    @Autowired OrderController controller;
    @Autowired TestData data;

    @Test
    void touchingLazyLinesInTheControllerFails() {
        Long id = data.paidOrders(50, 3).get(0);

        assertThatThrownBy(() -> controller.linesBroken(id))
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("lines");
    }

    @Test
    void mappingInsideTheServiceWorks() {
        Long id = data.paidOrders(50, 3).get(0);

        assertThat(controller.lines(id)).hasSize(3)
                .allSatisfy(line -> assertThat(line.productName()).startsWith("Report product"));
    }
}
