package com.multicloud.authservice.config;

import com.multicloud.authservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security config for auth-service.
 *
 * Key CORS rules:
 *  1. cors() must be enabled so Spring Security delegates to our CorsConfigurationSource
 *  2. OPTIONS requests must be permitted BEFORE JWT filter runs
 *  3. csrf must stay disabled (stateless API)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider  authenticationProvider;

    private static final String[] PUBLIC_ENDPOINTS = {

            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/validate-token",
            "/api/auth/swagger-ui.html",
            "/api/auth/swagger-ui/**",
            "/api/auth/v3/api-docs/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",

            "/actuator/**"
    };
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(AbstractHttpConfigurer::disable)  // ← disable here, gateway handles it
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/auth/users")
                        .hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * CorsConfigurationSource used by Spring Security's cors() DSL.
     * Must mirror the same origins as the Gateway's CorsWebFilter.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "https://your-prod-app.com"   // ← replace
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin",
                "Cache-Control"
        ));

        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}