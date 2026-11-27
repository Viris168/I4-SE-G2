package edu.itc.shop.web;

import edu.itc.shop.config.BearerProblemEntryPoint;
import edu.itc.shop.config.JwtConfig;
import edu.itc.shop.config.SecurityConfig;
import edu.itc.shop.config.SecurityProps;
import edu.itc.shop.domain.Order;
import edu.itc.shop.security.ProblemAccessDeniedHandler;
import edu.itc.shop.security.ProblemEntryPoint;
import edu.itc.shop.security.ProblemResponseWriter;
import edu.itc.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the HTTP side with JWT-authenticated requests: 401 for anonymous or a malformed token,
 * 403 for a customer on /ship, 204 for an admin.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtConfig.class, BearerProblemEntryPoint.class,
         ProblemEntryPoint.class, ProblemAccessDeniedHandler.class, ProblemResponseWriter.class})
@EnableConfigurationProperties(SecurityProps.class)
@TestPropertySource(properties = "JWT_SECRET=test-only-secret-0123456789-0123456789-abcdefghij")
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
    void malformedBearerTokenGets401() throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", "Bearer not.a.jwt"))
           .andExpect(status().isUnauthorized())
           .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(jsonPath("$.status").value(401))
           .andExpect(jsonPath("$.detail").value("Token expired or invalid"));
        verifyNoInteractions(service);
    }

    @Test
    void customerCannotShip() throws Exception {
        mvc.perform(post("/api/orders/1/ship").with(token("alice", "ROLE_CUSTOMER")))
           .andExpect(status().isForbidden())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(jsonPath("$.detail").value("Access denied"))
           .andExpect(jsonPath("$.instance").value("/api/orders/1/ship"));
        verifyNoInteractions(service);
    }

    @Test
    void adminShips() throws Exception {
        when(service.ship(anyLong())).thenReturn(new Order("alice", 2L, 1));

        mvc.perform(post("/api/orders/1/ship").with(token("admin", "ROLE_ADMIN", "ROLE_CUSTOMER")))
           .andExpect(status().isNoContent());
        verify(service).ship(1L);
    }

    /** A JWT shaped like the ones AuthController issues: subject plus a roles claim mapped to authorities. */
    private static JwtRequestPostProcessor token(String subject, String... roles) {
        return jwt().jwt(j -> j.subject(subject).claim("roles", List.of(roles)))
                    .authorities(Arrays.stream(roles).map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new));
    }
}
