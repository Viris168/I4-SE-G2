package edu.itc.shop.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "customer_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "street", nullable = false, length = 120)),
            @AttributeOverride(name = "city", column = @Column(name = "city", nullable = false, length = 80)),
            @AttributeOverride(name = "zip", column = @Column(name = "zip", nullable = false, length = 20))
    })
    private Address billing;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "ship_street", nullable = false, length = 120)),
            @AttributeOverride(name = "city", column = @Column(name = "ship_city", nullable = false, length = 80)),
            @AttributeOverride(name = "zip", column = @Column(name = "ship_zip", nullable = false, length = 20))
    })
    private Address shipping;

    protected Customer() {
    }

    public Customer(String name, String email, Address billing, Address shipping) {
        this.name = name;
        this.email = email;
        this.billing = billing;
        this.shipping = shipping;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Address getBilling() {
        return billing;
    }

    public Address getShipping() {
        return shipping;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Customer other && id != null && id.equals(other.getId()));
    }

    @Override
    public int hashCode() {
        return Customer.class.hashCode();
    }

    @Override
    public String toString() {
        return "Customer[id=%s, email=%s]".formatted(id, email);
    }
}
