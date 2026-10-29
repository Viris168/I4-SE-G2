package edu.itc.shop.order;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import tools.jackson.databind.ObjectMapper;
import edu.itc.shop.aspect.RequestScopeContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(RequestScopeContext.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @Test
    void shouldReturn201WhenPlacingValidOrder() throws Exception {
        PlaceOrder request = new PlaceOrder(1L, List.of(new PlaceOrder.Line(1L, 2)));
        OrderDto response = new OrderDto(10L, 1L, OrderStatus.NEW, Instant.now(), new BigDecimal("110.00"), List.of());

        when(orderService.place(any(PlaceOrder.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.total").value(110.00));
    }

    @Test
    void shouldReturn400WhenLinesAreEmpty() throws Exception {
        PlaceOrder request = new PlaceOrder(1L, List.of());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409WhenServiceThrowsConflict() throws Exception {
        PlaceOrder request = new PlaceOrder(1L, List.of(new PlaceOrder.Line(1L, 2)));

        when(orderService.place(any(PlaceOrder.class)))
                .thenThrow(new OrderConflictException("Not enough stock"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnPagedOrders() throws Exception {
        OrderDto dto = new OrderDto(1L, 1L, OrderStatus.NEW, Instant.now(), new BigDecimal("50.00"), List.of());
        Page<OrderDto> paged = new Page<>(List.of(dto), 0, 10, 1);

        when(orderService.list(1L, OrderStatus.NEW, 0, 10)).thenReturn(paged);

        mockMvc.perform(get("/api/orders?customerId=1&status=NEW&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(orderService.get(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn200WhenOrderCancelled() throws Exception {
        OrderDto response = new OrderDto(5L, 1L, OrderStatus.CANCELLED, Instant.now(), new BigDecimal("20.00"), List.of());
        when(orderService.cancel(5L)).thenReturn(response);

        mockMvc.perform(post("/api/orders/5/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
