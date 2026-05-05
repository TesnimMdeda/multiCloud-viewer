package com.multicloud.notificationservice.config;

import com.multicloud.notificationservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/**",
            "/error",
            "/api/notifications/send",
            "/api/notifications/stream"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (!response.isCommitted()) {
                                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.getWriter().write(
                                        "{\"error\":\"Unauthorized\",\"message\":\"" + authException.getMessage() + "\"}");
                            }
                        })
                        .accessDeniedHandler((request, response, deniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"error\":\"Forbidden\",\"message\":\"" + deniedException.getMessage() + "\"}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
