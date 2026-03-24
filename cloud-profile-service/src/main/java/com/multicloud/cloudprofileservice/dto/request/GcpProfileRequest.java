package com.multicloud.cloudprofileservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class GcpProfileRequest {

    @NotBlank(message = "Project ID is required")
    @Pattern(regexp = "^[a-z][a-z0-9\\-]{4,28}[a-z0-9]$",
            message = "Invalid GCP project ID format")
    private String projectId;

    @NotBlank(message = "Profile name is required")
    @Size(min = 2, max = 100)
    private String profileName;

    @NotBlank(message = "Region is required")
    private String region; // e.g. "us-central1"

    @NotNull(message = "Service Account Key is required")
    private MultipartFile serviceAccountKey; // .json file upload
}