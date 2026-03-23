package com.multicloud.authservice.controller;

import com.multicloud.authservice.dto.request.UpdateUserRequest;
import com.multicloud.authservice.dto.response.ApiResponse;
import com.multicloud.authservice.dto.response.UserResponse;
import com.multicloud.authservice.entity.User;
import com.multicloud.authservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "CRUD operations on users")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Get all users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getAllUsers(), "Users retrieved"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or #id == authentication.principal.id")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String id) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getUserById(id), "User retrieved"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateUser(id, request, currentUser), "User updated"));
    }

    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Lock or unlock a user account")
    public ResponseEntity<ApiResponse<Void>> setLock(
            @PathVariable String id,
            @RequestParam boolean locked,
            @AuthenticationPrincipal User currentUser) {
        userService.setAccountLocked(id, locked, currentUser);
        return ResponseEntity.ok(ApiResponse.success(null,
                locked ? "Account locked" : "Account unlocked"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Delete a user account")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        userService.deleteUser(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted"));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Change user role — SUPER_ADMIN only")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
            @PathVariable String id,
            @RequestParam String role,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.changeUserRole(id, role, currentUser), "Role updated"));
    }
}