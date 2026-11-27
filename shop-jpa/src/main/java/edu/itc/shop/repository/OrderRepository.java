package edu.itc.shop.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.web.dto.OrderSummary;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerEmailAndStatusOrderByPlacedAtDesc(String email, OrderStatus status);

    long countByStatus(OrderStatus status);

    Optional<Order> findFirstByCustomerIdOrderByPlacedAtDesc(Long customerId);

    @Query("""
            select new edu.itc.shop.web.dto.OrderSummaryRow(o.id, o.status, o.placedAt, c.name)
            from Order o join o.customer c
            where o.status = :status
            """)
    Page<OrderSummary> findByStatus(@Param("status") OrderStatus status, Pageable pageable);

    @Query("""
            select distinct o from Order o
              join fetch o.lines l
              join fetch l.product
             where o.customer.email = :email
             order by o.placedAt desc
            """)
    List<Order> findWithLinesByCustomerEmail(@Param("email") String email);

    @Query("select count(l) from OrderLine l where l.order.status = :status")
    long countLinesByStatus(@Param("status") OrderStatus status);

    // Task 5: entity list for the naive N+1 report
    List<Order> findByStatus(OrderStatus status);

    // Task 5 (Fix A)
    @Query("select distinct o from Order o join fetch o.lines where o.status = :s")
    List<Order> findByStatusWithLines(@Param("s") OrderStatus status);

    // Task 5 (Fix B)
    @EntityGraph(attributePaths = {"lines", "lines.product"})
    List<Order> findWithGraphByStatus(OrderStatus status);

    // Challenge: order feed, offset-based. A Slice reads size + 1 rows and runs no count query.
    // "left join" (customer is never null) makes H2 read orders first, in (placed_at, id) index order;
    // with an inner join it starts from the small customer table and has to sort every order.
    @Query("""
            select new edu.itc.shop.web.dto.OrderSummaryRow(o.id, o.status, o.placedAt, c.name)
            from Order o left join o.customer c
            """)
    Slice<OrderSummary> feedSlice(Pageable pageable);

    // Challenge: order feed, keyset-based. No @EntityGraph on purpose: fetching the customer adds an
    // inner join that makes H2 drive from the customer table and sort every order (70-85 ms, see README).
    // The customers of a page are loaded in one extra query instead (@BatchSize on Customer).
    Window<Order> findAllByOrderByPlacedAtDescIdDesc(ScrollPosition position, Limit limit);

    // Challenge: keyset as a row-value comparison. H2 can seek the (placed_at, id) index with this form;
    // the OR form that Spring Data generates for the Window above makes it walk the index from the top.
    @Query("""
            select new edu.itc.shop.web.dto.OrderSummaryRow(o.id, o.status, o.placedAt, c.name)
            from Order o left join o.customer c
            where (o.placedAt, o.id) < (:placedAt, :id)
            order by o.placedAt desc, o.id desc
            """)
    List<OrderSummary> feedAfter(@Param("placedAt") Instant placedAt, @Param("id") Long id, Limit limit);
}
