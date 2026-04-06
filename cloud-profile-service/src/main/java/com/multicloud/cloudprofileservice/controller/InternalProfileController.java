package com.multicloud.cloudprofileservice.controller;

import com.multicloud.cloudprofileservice.dto.response.ApiResponse;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.entity.CloudProfile;
import com.multicloud.cloudprofileservice.repository.CloudProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cloud/internal")
@RequiredArgsConstructor
@Tag(name = "Internal", description = "Internal endpoints for service-to-service communication")
public class InternalProfileController {

    private final CloudProfileRepository profileRepository;

    @GetMapping("/{profileId}")
    @Operation(summary = "Internal: Get base profile metadata",
            description = "Used by other microservices to identify provider and region.")
    public ResponseEntity<ApiResponse<CloudProfileResponse>> getInternalProfile(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        CloudProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        if (!profile.getOwnerId().equals(user.getUsername())) {
            throw new RuntimeException("Unauthorized profile access");
        }

        return ResponseEntity.ok(ApiResponse.success(
                CloudProfileResponse.builder()
                        .id(profile.getId())
                        .profileName(profile.getProfileName())
                        .provider(profile.getProvider().name())
                        .region(profile.getRegion())
                        .status(profile.getStatus().name())
                        .ownerId(profile.getOwnerId())
                        .build(),
                "Internal profile metadata retrieved"));
    }
}
