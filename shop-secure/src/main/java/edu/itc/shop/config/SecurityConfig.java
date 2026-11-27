package edu.itc.shop.config;

import edu.itc.shop.security.ProblemAccessDeniedHandler;
import edu.itc.shop.security.ProblemEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
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
            .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
            // Task 4: HTTP Basic and Bearer JWT both supported
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                .authenticationEntryPoint(bearerEntryPoint))
            .formLogin(form -> form.disable())
            // CSRF disabled: credentials are Basic/Bearer only, never cookies, so a browser can't
            // attach them to a cross-site request on its own.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
