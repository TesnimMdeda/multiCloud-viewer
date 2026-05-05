package com.multicloud.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String token = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                // Fallback: Check for JWT in HttpOnly cookie
                var cookie = exchange.getRequest().getCookies().getFirst("JWT-TOKEN");
                if (cookie != null) {
                    token = cookie.getValue();
                } else {
                    // Manual parsing as a fallback for some browser/gateway edge cases
                    String cookieHeader = exchange.getRequest().getHeaders().getFirst(org.springframework.http.HttpHeaders.COOKIE);
                    if (cookieHeader != null && cookieHeader.contains("JWT-TOKEN=")) {
                        for (String c : cookieHeader.split(";")) {
                            String trimmed = c.trim();
                            if (trimmed.startsWith("JWT-TOKEN=")) {
                                token = trimmed.substring("JWT-TOKEN=".length());
                                break;
                            }
                        }
                    }
                }
            }

            if (token == null || token.isEmpty()) {
                log.warn("Unauthorized request to {}: No JWT found in Authorization header or JWT-TOKEN cookie", exchange.getRequest().getURI());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                byte[] bytes = "{\"error\":\"Unauthorized\",\"message\":\"No JWT found in Authorization header or JWT-TOKEN cookie\",\"source\":\"api-gateway\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                return exchange.getResponse().writeWith(Mono.just(buffer));
            }

            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
                
                // Important: We must build the mutated request AND the mutated exchange
                var mutatedRequest = exchange.getRequest().mutate()
                        .header("X-User-Email", claims.getSubject())
                        .header("X-User-Role", claims.get("role", String.class))
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (Exception e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                byte[] bytes = ("{\"error\":\"Unauthorized\",\"message\":\"Invalid JWT: " + e.getMessage() + "\",\"source\":\"api-gateway\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                return exchange.getResponse().writeWith(Mono.just(buffer));
            }
        };
    }

    public static class Config {
    }
}