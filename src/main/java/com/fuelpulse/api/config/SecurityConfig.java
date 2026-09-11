package com.fuelpulse.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 1 security baseline.
 *
 * <p>There is no authentication yet, so this chain does one job: allow exactly the
 * endpoints that are meant to be public and refuse everything else. Phase 2 adds
 * the JWT resource server on top without changing this shape.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection defends against a browser attaching ambient
                // credentials — cookies — to a forged request. This API is stateless
                // and will carry its credential in an Authorization header, which a
                // cross-site form cannot set. Disabling it here is a consequence of
                // that design, not a shortcut. If session cookies are ever
                // introduced, this line must come back.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/stations/nearby").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        // Deny by default. A new controller is unreachable until
                        // someone deliberately opens it, so forgetting to add a rule
                        // fails closed instead of publishing an endpoint by accident.
                        .anyRequest().denyAll())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        // 'unsafe-inline' is present only because Swagger UI is served
                        // from this application. Disable the UI in production
                        // (springdoc.swagger-ui.enabled=false) and tighten this to
                        // default-src 'none' — a JSON API needs no script or style
                        // budget at all.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "frame-ancestors 'none'; " +
                                        "base-uri 'none'; " +
                                        "form-action 'none'")));

        return http.build();
    }
}