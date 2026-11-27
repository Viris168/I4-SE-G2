package edu.itc.shop.config;

import edu.itc.shop.security.ProblemAccessDeniedHandler;
import edu.itc.shop.security.ProblemEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, ProblemEntryPoint entryPoint,
                            ProblemAccessDeniedHandler accessDeniedHandler,
                            JwtAuthenticationConverter jwtConverter,
                            BearerProblemEntryPoint bearerEntryPoint) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                // Task 3, defence in depth: OrderService.ship is also @PreAuthorize("hasRole('ADMIN')")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/ship").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            // Task 4: HTTP Basic is gone; every request carries a Bearer JWT checked by the JwtDecoder
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                .authenticationEntryPoint(bearerEntryPoint))
            .formLogin(form -> form.disable())
            // CSRF disabled: credentials are Bearer tokens only, never cookies, so a browser can't
            // attach them to a cross-site request on its own.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    UserDetailsService temporaryUsers() {           // removed in Task 2
        return new InMemoryUserDetailsManager(
            User.withUsername("admin").password("{noop}admin").roles("ADMIN").build(),
            User.withUsername("alice").password("{noop}alice").roles("CUSTOMER").build(),
            // Task 3: a second customer for the owner-check transcript (bob must not see alice's order)
            User.withUsername("bob").password("{noop}bob").roles("CUSTOMER").build());
    }
}
