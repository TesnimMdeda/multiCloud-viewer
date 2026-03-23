package com.multicloud.authservice.dto.request;


import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[@#$%^&+=!]).*$",
            message = "Password must contain uppercase, number, and special character"
    )
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
