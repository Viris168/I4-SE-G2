package edu.itc.shop.customer;

import edu.itc.shop.aspect.Audited;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Audited
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    @Audited
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
}
