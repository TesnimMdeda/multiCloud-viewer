package com.multicloud.authservice.service;

import com.multicloud.authservice.dto.request.*;
import com.multicloud.authservice.dto.response.*;
import com.multicloud.authservice.entity.User;

public interface AuthService {
    UserResponse registerUser(RegisterUserRequest request, User creator);
    AuthResponse login(LoginRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void logout(String token);
}
