package edu.itc.shop.web;

import edu.itc.shop.service.OrderService;
import edu.itc.shop.service.PlaceOrder;
import edu.itc.shop.web.dto.OrderResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin HTTP layer: no role checks here, OrderService enforces them with
 * @PreAuthorize / @PostAuthorize.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** The customer is the authenticated user, never a field of the body. */
    @PostMapping
    public ResponseEntity<OrderResponse> place(@RequestBody @Valid PlaceOrder cmd, Authentication auth) {
        OrderResponse created = OrderResponse.of(service.place(auth.getName(), cmd));
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    /** Own history by default; ?customer=alice is only allowed for admins (enforced in the service). */
    @GetMapping
    public List<OrderResponse> history(@RequestParam(required = false) String customer, Authentication auth) {
        String who = (customer == null || customer.isBlank()) ? auth.getName() : customer;
        return service.history(who).stream().map(OrderResponse::of).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable long id) {
        return OrderResponse.of(service.get(id));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<Void> ship(@PathVariable long id) {
        service.ship(id);
        return ResponseEntity.noContent().build();
    }
}
