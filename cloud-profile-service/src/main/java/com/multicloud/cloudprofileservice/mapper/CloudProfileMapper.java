package com.multicloud.cloudprofileservice.mapper;

import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.entity.CloudProfile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Manual mapper — avoids MapStruct complexity for the dynamic "details" map.
 * Never maps any encrypted key fields into the response.
 */
@Component
public class CloudProfileMapper {

    public CloudProfileResponse toResponse(CloudProfile profile,
                                           Map<String, String> extractedDetails) {
        return CloudProfileResponse.builder()
                .id(profile.getId())
                .profileName(profile.getProfileName())
                .provider(profile.getProvider().name())
                .region(profile.getRegion())
                .status(profile.getStatus().name())
                .validationError(profile.getValidationError())
                .lastValidatedAt(profile.getLastValidatedAt())
                .createdAt(profile.getCreatedAt())
                .ownerId(profile.getOwnerId())          // ← added
                .details(extractedDetails)
                .build();
    }
}