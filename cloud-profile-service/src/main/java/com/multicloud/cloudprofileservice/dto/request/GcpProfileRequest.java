package com.multicloud.cloudprofileservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class GcpProfileRequest {

    @NotBlank(message = "Profile name is required")
    private String profileName;

    @NotBlank(message = "Project ID is required")
    @Pattern(regexp = "^[a-z][a-z0-9\\-]{4,28}[a-z0-9]$",
            message = "Invalid GCP project ID format")
    private String projectId;

    @NotBlank(message = "Region is required")
    private String region;

    @NotBlank(message = "Service account key JSON is required")
    private String serviceAccountKeyJson; // raw JSON content from uploaded file
}