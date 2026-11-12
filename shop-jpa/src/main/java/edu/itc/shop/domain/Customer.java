package edu.itc.shop.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "customer_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Embedded
    private Address billing;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "ship_street", nullable = false, length = 200)),
            @AttributeOverride(name = "city", column = @Column(name = "ship_city", nullable = false, length = 100)),
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

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Address getBilling() {
        return billing;
    }

    public void setBilling(Address billing) {
        this.billing = billing;
    }

    public Address getShipping() {
        return shipping;
    }

    public void setShipping(Address shipping) {
        this.shipping = shipping;
    }
}
