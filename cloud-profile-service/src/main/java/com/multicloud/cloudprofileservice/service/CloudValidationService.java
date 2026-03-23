package com.multicloud.cloudprofileservice.service;

import com.multicloud.cloudprofileservice.entity.CloudProfile;

public interface CloudValidationService {

    /**
     * Validates the credentials stored in the profile against the real
     * cloud provider API. Throws CloudValidationException if rejected.
     * Returns enriched metadata to store in the profile.
     */
    ValidationResult validate(CloudProfile profile);

    record ValidationResult(
            boolean valid,
            String message,
            String extractedMetadata1,  // GCP: serviceAccountEmail / OCI: tenancyName
            String extractedMetadata2   // GCP: projectNumber       / OCI: homeRegion
    ) {}
}