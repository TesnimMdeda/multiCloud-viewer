package com.multicloud.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Global CORS configuration for the API Gateway (Spring WebFlux / reactive).
 *
 * This must be a CorsWebFilter bean — NOT a WebMvcConfigurer — because the
 * Gateway runs on Spring WebFlux, not Spring MVC.
 *
 * It must be applied BEFORE the JwtAuthFilter so that OPTIONS preflight
 * requests are answered with 200 without ever hitting JWT validation.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // ── Allowed origins ───────────────────────────────────────────────
        // Add every frontend origin you need: local dev + production URL
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",   // Angular dev server
                "https://your-prod-app.com" // ← replace with your real domain
        ));

        // ── Allowed methods (include OPTIONS so preflight passes) ─────────
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // ── Allowed headers ───────────────────────────────────────────────
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin",
                "Cache-Control"
        ));

        // ── Expose Authorization header to Angular's HttpClient ───────────
        config.setExposedHeaders(List.of("Authorization"));

        // ── Allow cookies / credentials ───────────────────────────────────
        config.setAllowCredentials(true);

        // ── Cache preflight response for 1 hour ───────────────────────────
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);   // apply to ALL routes

        return new CorsWebFilter(source);
    }
}