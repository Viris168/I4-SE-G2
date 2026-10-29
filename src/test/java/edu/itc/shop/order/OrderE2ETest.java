package edu.itc.shop.order;

import edu.itc.shop.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class OrderE2ETest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldPlaceOrderCheckStockCancelAndCheckAgain() {
        // Initial stock of product 2 ('Limited Item') in data-test.sql is 2
        Long targetProductId = 2L;
        int initialStock = productRepository.findById(targetProductId).orElseThrow().stock();
        assertThat(initialStock).isEqualTo(2);

        // 1. Place order for 2 units
        PlaceOrder placeRequest = new PlaceOrder(1L, List.of(new PlaceOrder.Line(targetProductId, 2)));

        OrderDto placedOrder = restTestClient.post()
                .uri("/api/orders")
                .body(placeRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(placedOrder).isNotNull();
        assertThat(placedOrder.id()).isNotNull();
        assertThat(placedOrder.status()).isEqualTo(OrderStatus.NEW);

        // 2. Check stock: should now be 0
        int stockAfterOrder = productRepository.findById(targetProductId).orElseThrow().stock();
        assertThat(stockAfterOrder).isEqualTo(0);

        // Attempting to order again should fail with 409 Conflict because out of stock
        restTestClient.post()
                .uri("/api/orders")
                .body(new PlaceOrder(1L, List.of(new PlaceOrder.Line(targetProductId, 1))))
                .exchange()
                .expectStatus().isEqualTo(409);

        // 3. Cancel order
        OrderDto cancelledOrder = restTestClient.post()
                .uri("/api/orders/" + placedOrder.id() + "/cancel")
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(cancelledOrder).isNotNull();
        assertThat(cancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);

        // 4. Check stock again: should be restored back to 2
        int stockAfterCancel = productRepository.findById(targetProductId).orElseThrow().stock();
        assertThat(stockAfterCancel).isEqualTo(2);
    }
}
