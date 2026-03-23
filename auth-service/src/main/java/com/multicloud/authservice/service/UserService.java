package com.multicloud.authservice.service;

import com.multicloud.authservice.dto.request.UpdateUserRequest;
import com.multicloud.authservice.dto.response.UserResponse;
import com.multicloud.authservice.entity.User;

import java.util.List;

public interface UserService {
    List<UserResponse> getAllUsers();
    UserResponse getUserById(String userId);
    UserResponse getUserByEmail(String email);
    UserResponse updateUser(String userId, UpdateUserRequest request, User currentUser);

    void setAccountLocked(String userId, boolean locked, User currentUser);

    void deleteUser(String userId, User currentUser);

    UserResponse changeUserRole(String userId, String newRole, User currentUser);
}