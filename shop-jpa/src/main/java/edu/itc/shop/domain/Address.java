package edu.itc.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Address(
        @Column(nullable = false, length = 200) String street,
        @Column(nullable = false, length = 100) String city,
        @Column(nullable = false, length = 20) String zip) {
}
