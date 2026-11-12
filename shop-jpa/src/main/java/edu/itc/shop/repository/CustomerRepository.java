package edu.itc.shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.itc.shop.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
