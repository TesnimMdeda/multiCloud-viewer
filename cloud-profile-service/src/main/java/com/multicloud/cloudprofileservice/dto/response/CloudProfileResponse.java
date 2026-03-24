package com.multicloud.cloudprofileservice.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class CloudProfileResponse {

    private String id;
    private String profileName;
    private String provider;     // "GCP", "OCI"...
    private String region;
    private String status;       // VALID / INVALID / PENDING_VALIDATION
    private String validationError; // null if valid
    private LocalDateTime lastValidatedAt;
    private LocalDateTime createdAt;

    // Provider-specific details (safe — never exposes keys)
    private Map<String, String> details;
}