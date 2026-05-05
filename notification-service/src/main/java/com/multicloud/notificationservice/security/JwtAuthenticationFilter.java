package com.multicloud.notificationservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.util.WebUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authentication filter that supports two strategies (in priority order):
 *
 * 1. Gateway-forwarded headers  →  X-User-Email + X-User-Role
 *    The API gateway validates the JWT and injects these headers before forwarding
 *    the request. Trusting them avoids redundant JWT re-parsing in every service.
 *
 * 2. Raw JWT token  →  Authorization: Bearer <token>  or  ?token=<value>
 *    Used as a fallback for direct calls (e.g. local dev, Swagger, integration tests)
 *    and for SSE connections where EventSource cannot send custom headers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    // Header names set by the API gateway after JWT validation
    private static final String GATEWAY_EMAIL_HEADER = "X-User-Email";
    private static final String GATEWAY_ROLE_HEADER  = "X-User-Role";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Skip if already authenticated (e.g. by another filter)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String userEmail = null;
        String userRole  = null;

        // ── Strategy 1: Trust gateway-forwarded headers ───────────────────────
        String gatewayEmail = request.getHeader(GATEWAY_EMAIL_HEADER);
        String gatewayRole  = request.getHeader(GATEWAY_ROLE_HEADER);

        if (gatewayEmail != null && !gatewayEmail.isBlank()) {
            userEmail = gatewayEmail;
            userRole  = (gatewayRole != null && !gatewayRole.isBlank()) ? gatewayRole : "CLIENT";
            log.debug("Authenticated via gateway headers: user={}, role={}", userEmail, userRole);
        }

        // ── Strategy 2: Fallback – parse raw JWT ─────────────────────────────
        if (userEmail == null) {
            String token = extractRawToken(request);
            if (token != null) {
                try {
                    if (jwtService.isTokenValid(token)) {
                        userEmail = jwtService.extractUsername(token);
                        String extracted = jwtService.extractRole(token);
                        userRole  = (extracted != null && !extracted.isBlank()) ? extracted : "CLIENT";
                        log.debug("Authenticated via JWT token: user={}, role={}", userEmail, userRole);
                    }
                } catch (Exception e) {
                    log.warn("JWT parsing failed for request [{}]: {}", request.getRequestURI(), e.getMessage());
                }
            }
        }

        // ── Set authentication in security context ────────────────────────────
        if (userEmail != null) {
            UserDetails userDetails = User.builder()
                    .username(userEmail)
                    .password("")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + userRole)))
                    .build();

            var auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT string from either the Authorization header or the
     * {@code ?token=} query parameter (needed for SSE / EventSource connections).
     */
    private String extractRawToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Fallback 1: Check for JWT in 'token' query parameter (common for SSE / EventSource)
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }

        // Fallback 2: Check for JWT in HttpOnly cookie
        Cookie cookie = WebUtils.getCookie(request, "JWT-TOKEN");
        return (cookie != null) ? cookie.getValue() : null;
    }
}
