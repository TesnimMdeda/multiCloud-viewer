package com.multicloud.cloudprofileservice.controller;

import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.ApiResponse;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.service.CloudProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cloud/profiles")
@RequiredArgsConstructor
@Tag(name = "Cloud Profiles", description = "Manage cloud provider credentials")
public class CloudProfileController {

    private final CloudProfileService profileService;

    // ─── GCP ────────────────────────────────────────────────────────────────

    @PostMapping(value = "/gcp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add GCP profile — validates service account key against GCP SDK")
    public ResponseEntity<ApiResponse<CloudProfileResponse>> createGcpProfile(
            @Valid @ModelAttribute GcpProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {

        var result = profileService.createGcpProfile(request, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result,
                        "GCP profile created and validated successfully"));
    }

    // ─── OCI ────────────────────────────────────────────────────────────────

    @PostMapping(value = "/oci", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add OCI profile — validates key/fingerprint against OCI SDK")
    public ResponseEntity<ApiResponse<CloudProfileResponse>> createOciProfile(
            @Valid @ModelAttribute OciProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {

        var result = profileService.createOciProfile(request, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "OCI profile created successfully"));
    }

    // ─── LIST ALL PROFILES FOR CURRENT USER ─────────────────────────────────

    @GetMapping
    @Operation(summary = "List all profiles owned by the authenticated user")
    public ResponseEntity<ApiResponse<List<CloudProfileResponse>>> getMyProfiles(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                profileService.getProfilesByOwner(user.getUsername()),
                "Profiles retrieved"));
    }

    // ─── DELETE ─────────────────────────────────────────────────────────────

    @DeleteMapping("/{profileId}")
    @Operation(summary = "Delete a cloud profile (owner only)")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        profileService.deleteProfile(profileId, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "Profile deleted"));
    }
}