package edu.itc.shop.customer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepository {

    private final JdbcClient jdbcClient;

    public CustomerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Customer> findById(Long id) {
        return jdbcClient.sql("SELECT id, name, email FROM customer WHERE id = :id")
                .param("id", id)
                .query(Customer.class)
                .optional();
    }

    public List<Customer> findAll() {
        return jdbcClient.sql("SELECT id, name, email FROM customer")
                .query(Customer.class)
                .list();
    }
}
