package edu.itc.shop;

import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ShopSecureApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShopSecureApplication.class, args);
	}

	/** Seeds the catalogue once; does nothing if products already exist. */
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
