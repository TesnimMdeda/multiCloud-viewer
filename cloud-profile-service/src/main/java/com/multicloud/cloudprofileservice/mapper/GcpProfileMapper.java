package com.multicloud.cloudprofileservice.mapper;

import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.entity.CloudProfile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GcpProfileMapper {

    public CloudProfileResponse toResponse(CloudProfile profile,
                                           Map<String, String> details,
                                           List<String> buckets,
                                           String connectionMessage) {
        return CloudProfileResponse.builder()
                .id(profile.getId())
                .profileName(profile.getProfileName())
                .provider(profile.getProvider().name())
                .region(profile.getRegion())
                .status(profile.getStatus().name())
                .validationError(profile.getValidationError())
                .lastValidatedAt(profile.getLastValidatedAt())
                .createdAt(profile.getCreatedAt())
                .ownerId(profile.getOwnerId())
                .details(details)
                .buckets(buckets)
                .connectionMessage(connectionMessage)
                .build();
    }
}