package edu.itc.shop.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.service.OrderService;
import edu.itc.shop.web.dto.LineDto;
import edu.itc.shop.web.dto.OrderSummary;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orders;
    private final OrderService orderService;

    public OrderController(OrderRepository orders, OrderService orderService) {
        this.orders = orders;
        this.orderService = orderService;
    }

    @GetMapping
    public Page<OrderSummary> list(@RequestParam OrderStatus status,
                                   @PageableDefault(size = 20, sort = "placedAt",
                                           direction = Sort.Direction.DESC) Pageable pageable) {
        return orders.findByStatus(status, pageable);
    }

    // Task 5: deliberately broken. findById's transaction is already closed and open-in-view=false,
    // so getLines() throws LazyInitializationException (HTTP 500). Kept as documentation.
    @GetMapping("/{id}/lines-broken")
    public List<LineDto> linesBroken(@PathVariable Long id) {
        return orders.findById(id).orElseThrow().getLines().stream()
                .map(line -> new LineDto(
                        line.getProduct().getSku(),
                        line.getProduct().getName(),
                        line.getQuantity(),
                        line.getUnitPrice()))
                .toList();
    }

    // Task 5: the fix, mapping happens inside OrderService's read-only transaction
    @GetMapping("/{id}/lines")
    public List<LineDto> lines(@PathVariable Long id) {
        return orderService.findLines(id);
    }
}
