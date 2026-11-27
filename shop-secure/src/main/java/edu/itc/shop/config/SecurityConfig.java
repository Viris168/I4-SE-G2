package edu.itc.shop.config;

import edu.itc.shop.security.ProblemAccessDeniedHandler;
import edu.itc.shop.security.ProblemEntryPoint;
import edu.itc.shop.security.RequestIdFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSP = "default-src 'self'; frame-ancestors 'none'; object-src 'none'";

    @Bean
    @Order(1)
    SecurityFilterChain api(HttpSecurity http, ProblemEntryPoint entryPoint,
                            ProblemAccessDeniedHandler accessDeniedHandler,
                            JwtAuthenticationConverter jwtConverter,
                            BearerProblemEntryPoint bearerEntryPoint) throws Exception {
        http.securityMatcher("/api/**")
            .cors(Customizer.withDefaults())
            // CSRF disabled: this chain accepts Authorization headers only (never cookies); see docs/csrf.md
            .csrf(csrf -> csrf.disable())
            .headers(SecurityConfig::applyHardenedHeaders)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                // Task 3, defence in depth: OrderService.ship is also @PreAuthorize("hasRole('ADMIN')")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/ship").hasRole("ADMIN")
                .anyRequest().authenticated())
            // Task 4: Bearer JWT resource server; HTTP Basic is gone, credentials only go to /api/auth/login
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                .authenticationEntryPoint(bearerEntryPoint))
            .formLogin(form -> form.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(new RequestIdFilter(), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain pages(HttpSecurity http) throws Exception {
        http.securityMatcher("/products/**")
            .headers(SecurityConfig::applyHardenedHeaders)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/products/*/reviews.html").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/products/*/reviews.html").permitAll()
                .anyRequest().denyAll())
            .addFilterBefore(new RequestIdFilter(), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain fallback(HttpSecurity http) throws Exception {
        http.headers(SecurityConfig::applyHardenedHeaders)
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .anyRequest().denyAll())
            .addFilterBefore(new RequestIdFilter(), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    private static void applyHardenedHeaders(HeadersConfigurer<HttpSecurity> h) {
        h.frameOptions(f -> f.deny())
         // AnyRequestMatcher lets HSTS show over plain HTTP on localhost during the lab; production uses default HTTPS-only
         .httpStrictTransportSecurity(hsts -> hsts
             .requestMatcher(AnyRequestMatcher.INSTANCE)
             .includeSubDomains(true)
             .maxAgeInSeconds(31_536_000))
         .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
         .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
    }
}
