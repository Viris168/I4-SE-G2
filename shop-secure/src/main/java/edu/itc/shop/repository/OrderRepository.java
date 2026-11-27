package edu.itc.shop.repository;

import edu.itc.shop.domain.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomer(String customer);
}
