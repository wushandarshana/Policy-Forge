package com.horizon.policyforge.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication boundary (NFR-04): HTTP Basic with the four in-memory synthetic users from app_spec section 2.
 * Public: health, the catalog reads, quote create and read, and the SPA. Every other {@code /api/**} path needs a
 * logged-in user, and controllers add role checks with {@code @PreAuthorize}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Synthetic credentials only (app_spec section 2). Not for any real deployment. */
    private static final String SYNTHETIC_PASSWORD = "password";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        ProblemJsonSecurityHandler problemHandler = new ProblemJsonSecurityHandler(objectMapper);
        http
                // Stateless API authenticated per request with Basic credentials. Browsers can still replay cached Basic
                // credentials cross-site, so every state-changing endpoint consumes application/json only and CORS stays
                // unconfigured: a cross-site JSON request needs a preflight that fails, and text/plain is a 415 (NFR-04.3).
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint(problemHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Public paths are listed exactly (app_spec section 8): new endpoints default to authenticated.
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*/versions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/quotes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/quotes/*").permitAll()
                        .requestMatchers("/api/**", "/actuator/**").authenticated()
                        // Everything else is the SPA (index.html, /assets/**, client-side routes) or /error.
                        // No data is served outside /api and /actuator.
                        .anyRequest().permitAll())
                .headers(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        String encoded = encoder.encode(SYNTHETIC_PASSWORD);
        return new InMemoryUserDetailsManager(
                User.withUsername("customer1").password(encoded).roles("CUSTOMER").build(),
                User.withUsername("customer2").password(encoded).roles("CUSTOMER").build(),
                User.withUsername("underwriter1").password(encoded).roles("UNDERWRITER").build(),
                User.withUsername("admin1").password(encoded).roles("ADMIN", "UNDERWRITER", "PRODUCT_MANAGER").build());
    }
}
