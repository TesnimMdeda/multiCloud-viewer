package com.multicloud.cloudprofileservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    // NOTE: No UserDetailsService here — this service validates the JWT
    // directly using the shared secret. User data comes from the token claims
    // forwarded by the Gateway (X-User-Email, X-User-Role headers).

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        if (path.contains("swagger-ui") ||
                path.contains("v3/api-docs")) {

            filterChain.doFilter(request, response);
            return;
        }

        // Gateway forwards these headers after validating the token
        String userEmail = request.getHeader("X-User-Email");
        String userRole  = request.getHeader("X-User-Role");


        // Fallback: also accept raw Bearer token (direct calls without Gateway)
        if (userEmail == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtService.isTokenValid(token)) {
                        userEmail = jwtService.extractUsername(token);
                        userRole  = jwtService.extractRole(token);
                    }
                } catch (Exception e) {
                    log.warn("JWT parsing failed: {}", e.getMessage());
                }
            }
        }

        if (userEmail != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            var authority = new SimpleGrantedAuthority(
                    "ROLE_" + (userRole != null ? userRole : "CLIENT"));
            var auth = new UsernamePasswordAuthenticationToken(
                    userEmail, null, List.of(authority));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}