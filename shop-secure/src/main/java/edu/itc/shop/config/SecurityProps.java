package edu.itc.shop.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * shop.security.* from application.yml. The JWT secret comes from the JWT_SECRET environment
 * variable and must be at least 32 bytes, the minimum for HMAC-SHA256; start-up fails otherwise.
 */
@Validated
@ConfigurationProperties("shop.security")
public record SecurityProps(
        @NotBlank @Size(min = 32) String jwtSecret,
        @NotNull Duration accessTokenTtl) {
}
