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
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
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

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // ── 1. Try headers injected by the API Gateway ───────────────────────
        String userEmail = request.getHeader("X-User-Email");
        String userRole  = request.getHeader("X-User-Role");

        // ── 2. Fallback: parse the raw Bearer token (direct calls / Swagger) ─
        if (userEmail == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtService.isTokenValid(token)) {
                        userEmail = jwtService.extractUsername(token);
                        userRole  = jwtService.extractRole(token);
                        log.debug("JWT parsed directly — user: {}, role: {}", userEmail, userRole);
                    } else {
                        log.warn("JWT token is expired or invalid");
                    }
                } catch (Exception e) {
                    log.warn("JWT parsing failed: {}", e.getMessage());
                }
            }
        }

        // ── 3. Build authentication principal if we resolved a user ──────────
        if (userEmail != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String role = (userRole != null && !userRole.isBlank()) ? userRole : "CLIENT";

            // Build a UserDetails so @AuthenticationPrincipal resolves correctly
            UserDetails userDetails = User.builder()
                    .username(userEmail)
                    .password("")          // stateless — no password check needed
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                    .build();

            var auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("Authentication set for user: {} with role: {}", userEmail, role);
        }

        filterChain.doFilter(request, response);
    }
}