package com.multicloud.cloudprofile.dto.response;

import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.entity.ProfileStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CloudProfileResponse {
    private UUID id;
    private String profileName;
    private String region;
    private CloudProvider provider;
    private ProfileStatus status;
    private String validationMessage;
    private String createdByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime lastValidatedAt;
    // provider-specific fields populated by subclass mappers
    private String projectId;         // GCP
    private String serviceAccountEmail; // GCP
    private String tenancyOcid;       // OCI
    private String userOcid;          // OCI
    private String fingerprint;       // OCI
}