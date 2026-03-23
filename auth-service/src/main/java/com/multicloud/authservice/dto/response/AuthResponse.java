package com.multicloud.authservice.dto.response;

import lombok.*;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserResponse user;
}