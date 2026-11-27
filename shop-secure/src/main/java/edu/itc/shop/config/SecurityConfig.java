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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, ProblemEntryPoint entryPoint,
                            ProblemAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                // Task 3, defence in depth: OrderService.ship is also @PreAuthorize("hasRole('ADMIN')")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/ship").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
            .formLogin(form -> form.disable())
            // CSRF disabled: this chain accepts no cookie-based credentials (Basic / Bearer only),
            // so a cross-site request cannot ride on a credential the browser attaches automatically.
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
