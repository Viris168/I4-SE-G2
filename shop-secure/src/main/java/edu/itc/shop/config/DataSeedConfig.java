package edu.itc.shop.config;

import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the catalogue once; does nothing if products already exist.
 * Kept out of ShopSecureApplication so @WebMvcTest slices (which have no JPA
 * repositories) don't try to create it.
 */
@Configuration
public class DataSeedConfig {

	@Bean
	CommandLineRunner seedProducts(ProductRepository products) {
		return args -> {
			if (products.count() == 0) {
				products.saveAll(List.of(
						new Product("USB-C cable", new BigDecimal("7.90"), 25),
						new Product("Wireless mouse", new BigDecimal("19.90"), 10),
						new Product("Mechanical keyboard", new BigDecimal("89.00"), 4)));
			}
		};
	}
}
