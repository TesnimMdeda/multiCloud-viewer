package com.multicloud.authservice.controller;

import com.multicloud.authservice.dto.request.*;
import com.multicloud.authservice.dto.response.*;
import com.multicloud.authservice.entity.User;
import com.multicloud.authservice.service.AuthService;
import com.multicloud.authservice.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Auth & User management endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);

        // Set JWT in HttpOnly cookie for secure SSE/EventSource support
        ResponseCookie cookie = ResponseCookie.from("JWT-TOKEN", authResponse.getAccessToken())
                .httpOnly(true)
                .secure(false) // Set to true in production with HTTPS
                .path("/")
                .maxAge(authResponse.getExpiresIn() / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(
                ApiResponse.success(authResponse, "Login successful"));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Register a new user (SUPER_ADMIN or ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> registerUser(
            @Valid @RequestBody RegisterUserRequest request,
            @AuthenticationPrincipal User currentUser) {
        UserResponse response = authService.registerUser(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User created successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Send password reset email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Reset email sent if account exists"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Password reset successfully"));
    }

    @GetMapping("/validate-token")
    @Operation(summary = "Validate password reset token")
    public ResponseEntity<ApiResponse<Void>> validateToken(
            @RequestParam String token) {
        authService.validateToken(token);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Token is valid"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current user")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletResponse response) {
        authService.logout(authHeader.substring(7));

        // Clear the JWT cookie
        ResponseCookie cookie = ResponseCookie.from("JWT-TOKEN", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(
                ApiResponse.success(null, "Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user info")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal User currentUser) {
        // userMapper injected in a real impl
        return ResponseEntity.ok(
                ApiResponse.success(null, "Current user"));
    }
}