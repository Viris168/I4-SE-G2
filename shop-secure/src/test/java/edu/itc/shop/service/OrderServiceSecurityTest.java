package edu.itc.shop.service;

import edu.itc.shop.domain.Order;
import edu.itc.shop.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Proves the @PreAuthorize rules on OrderService, independent of HTTP. */
@SpringBootTest
@TestPropertySource(properties = "JWT_SECRET=test-only-secret-0123456789-0123456789-abcdefghij")
class OrderServiceSecurityTest {

    @Autowired OrderService service;
    @MockitoBean OrderRepository orders;

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void aliceReadsHerOwnHistory() {
        when(orders.findByCustomer("alice")).thenReturn(List.of(new Order("alice", 2L, 1)));

        assertThat(service.history("alice")).hasSize(1);
        verify(orders).findByCustomer("alice");
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void aliceCannotReadBobsHistory() {
        assertThrows(AccessDeniedException.class, () -> service.history("bob"));
        verifyNoInteractions(orders);          // rejected BEFORE the method ran
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminReadsBobsHistory() {
        when(orders.findByCustomer("bob")).thenReturn(List.of(new Order("bob", 1L, 3)));

        assertThat(service.history("bob")).extracting(Order::getCustomer).containsExactly("bob");
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void aliceCannotShip() {
        assertThrows(AccessDeniedException.class, () -> service.ship(1L));
        verifyNoInteractions(orders);
    }

    @Test
    void noUserAtAll() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.history("alice"));
        verifyNoInteractions(orders);
    }
}
