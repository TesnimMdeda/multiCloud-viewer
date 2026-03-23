package com.multicloud.authservice.dto.request;

import com.multicloud.authservice.entity.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterUserRequest {
    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank @Email(message = "Valid email required")
    private String email;

    @NotBlank(message = "Company is required")
    private String company;

    private String avatar;

    @NotNull(message = "Role is required")
    private Role role;
}
