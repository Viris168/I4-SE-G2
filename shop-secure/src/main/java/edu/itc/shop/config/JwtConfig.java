package edu.itc.shop.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Issues and validates the HS256 access tokens, and checks login passwords. */
@Configuration
public class JwtConfig {

    public static final String ISSUER = "shop-api";
    public static final String ROLES_CLAIM = "roles";

    @Bean
    SecretKey jwtKey(SecurityProps props) {
        return new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /** Checks the signature, the expiry (exp/nbf) and that iss is shop-api on every request. */
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    /** The roles claim already holds full authority names such as ROLE_CUSTOMER, so no prefix is added. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(ROLES_CLAIM);
        roles.setAuthorityPrefix("");
        var conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(roles);
        return conv;
    }

    /**
     * Used only by the login endpoint: looks the user up (Task 2) and compares the password hash.
     * Uses PasswordConfig's encoder (Task 2); until it exists, Spring's delegating encoder.
     */
    @Bean
    AuthenticationManager authenticationManager(ObjectProvider<UserDetailsService> uds, ObjectProvider<PasswordEncoder> encoder) {
        UserDetailsService userDetailsService = uds.getIfAvailable();
        if (userDetailsService == null) {
            return authentication -> {
                throw new BadCredentialsException("No UserDetailsService available");
            };
        }
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder.getIfAvailable(PasswordEncoderFactories::createDelegatingPasswordEncoder));
        return new ProviderManager(provider);
    }
}
