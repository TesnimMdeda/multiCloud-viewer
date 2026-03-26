package com.multicloud.cloudprofileservice.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CloudProfileResponse {

    private String id;
    private String profileName;
    private String provider;
    private String region;
    private String status;
    private String validationError;
    private LocalDateTime lastValidatedAt;
    private LocalDateTime createdAt;
    private String ownerId;

    /** Provider-specific metadata (safe — never exposes raw keys). */
    private Map<String, String> details;

    /**
     * Real resources fetched at creation time to confirm the credentials
     * have actual permissions. Empty list = connected but no resources yet.
     * Null = resource fetch not attempted.
     */
    private List<String> buckets;
    private String connectionMessage;
}