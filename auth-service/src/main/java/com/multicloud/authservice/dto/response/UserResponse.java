package com.multicloud.authservice.dto.response;

import com.multicloud.authservice.entity.Role;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder
public class UserResponse {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String company;
    private String avatar;
    private Role role;
    private boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
}
