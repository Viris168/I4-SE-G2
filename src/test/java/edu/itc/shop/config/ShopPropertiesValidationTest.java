package edu.itc.shop.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class ShopPropertiesValidationTest {

    @Configuration
    @EnableConfigurationProperties(ShopProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void validPropertiesBind() {
        runner.withPropertyValues("shop.currency=USD", "shop.tax-rate=0.10", "shop.low-stock-threshold=5")
                .run(ctx -> assertThat(ctx).hasNotFailed().getBean(ShopProperties.class)
                        .extracting(ShopProperties::currency).isEqualTo("USD"));
    }

    @Test
    void invalidPropertiesStopStartUp() {
        runner.withPropertyValues("shop.currency=", "shop.tax-rate=1.50", "shop.low-stock-threshold=-1")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("shop"));
    }
}
