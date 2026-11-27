package edu.itc.shop.web;

import edu.itc.shop.config.SecurityConfig;
import edu.itc.shop.domain.Order;
import edu.itc.shop.security.ProblemAccessDeniedHandler;
import edu.itc.shop.security.ProblemEntryPoint;
import edu.itc.shop.security.ProblemResponseWriter;
import edu.itc.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves the HTTP side: 401 for anonymous, 403 for a customer on /ship, 204 for an admin. */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, ProblemEntryPoint.class, ProblemAccessDeniedHandler.class, ProblemResponseWriter.class})
class OrderControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/orders"))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void customerCannotShip() throws Exception {
        mvc.perform(post("/api/orders/1/ship"))
           .andExpect(status().isForbidden())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(jsonPath("$.detail").value("Access denied"))
           .andExpect(jsonPath("$.instance").value("/api/orders/1/ship"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminShips() throws Exception {
        when(service.ship(anyLong())).thenReturn(new Order("alice", 2L, 1));

        mvc.perform(post("/api/orders/1/ship"))
           .andExpect(status().isNoContent());
        verify(service).ship(1L);
    }
}
