package com.multicloud.cloudprofileservice.controller;

import com.multicloud.cloudprofileservice.dto.response.AllCloudProfilesResponse;
import com.multicloud.cloudprofileservice.dto.response.ApiResponse;
import com.multicloud.cloudprofileservice.service.CloudProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cloud/profiles")
@RequiredArgsConstructor
@Tag(name = "Cloud Profiles", description = "Aggregate operations for all cloud provider profiles")
public class CloudProfileController {

    private final CloudProfileService cloudProfileService;

    @GetMapping("/all")
    @Operation(summary = "List all cloud profiles (GCP, OCI, etc.) with totals",
            description = "Returns an aggregate list of all cloud profiles for the authenticated user, including total counts per provider.")
    public ResponseEntity<ApiResponse<AllCloudProfilesResponse>> getAllProfiles(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                cloudProfileService.getAllCloudProfiles(user.getUsername()),
                "All cloud profiles retrieved successfully"));
    }
}
