package edu.itc.shop;

import edu.itc.shop.domain.Role;
import edu.itc.shop.repository.UserAccountRepository;
import edu.itc.shop.service.AccountService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.util.Set;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopSecureApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShopSecureApplication.class, args);
	}

	@Bean
	CommandLineRunner seedUsers(ObjectProvider<AccountService> accountService,
			ObjectProvider<UserAccountRepository> repository,
			@Value("${shop.seed.admin-password:}") String adminPassword,
			@Value("${shop.seed.customer-password:}") String customerPassword) {
		return args -> {
			AccountService accounts = accountService.getIfAvailable();
			UserAccountRepository users = repository.getIfAvailable();
			if (accounts != null && users != null) {
				if (!adminPassword.isBlank() && !users.existsByUsername("admin")) {
					accounts.register("admin", adminPassword, Set.of(Role.ADMIN, Role.CUSTOMER));
				}
				if (!customerPassword.isBlank() && !users.existsByUsername("alice")) {
					accounts.register("alice", customerPassword, Set.of(Role.CUSTOMER));
				}
			}
		};
	}
}
