package edu.itc.shop;

import edu.itc.shop.service.ReportBuilder;
import edu.itc.shop.service.WelcomeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopApplication {

	private static final Logger log = LoggerFactory.getLogger(ShopApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ShopApplication.class, args);
	}

	@Bean
	public CommandLineRunner runner(
			ApplicationContext ctx,
			ObjectProvider<WelcomeService> welcomeServiceProvider,
			ObjectProvider<ReportBuilder> reportBuilderProvider
	) {
		return args -> {
			WelcomeService welcomeService = welcomeServiceProvider.getIfAvailable();
			if (welcomeService == null) {
				return;
			}

			int beanCount = ctx.getBeanDefinitionCount();
			String[] notifiers = Arrays.stream(ctx.getBeanDefinitionNames())
					.filter(name -> name.toLowerCase().contains("notifier"))
					.sorted()
					.toArray(String[]::new);
			log.info("{} beans; notifiers = {}", beanCount, Arrays.toString(notifiers));

			welcomeService.welcome("ann", false);
			welcomeService.welcome("bob", true);

			ReportBuilder r1 = reportBuilderProvider.getObject();
			ReportBuilder r2 = reportBuilderProvider.getObject();
			log.info("prototype instances differ: {}", System.identityHashCode(r1) != System.identityHashCode(r2));
		};
	}
}
