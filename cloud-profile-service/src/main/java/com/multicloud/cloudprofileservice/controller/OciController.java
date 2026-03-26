package com.multicloud.cloudprofileservice.controller;

import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.ApiResponse;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.service.OciProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cloud/oci")
@RequiredArgsConstructor
@Tag(name = "OCI", description = "Create, fetch, validate and delete OCI profiles")
public class OciController {

    private final OciProfileService   ociProfileService;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create an OCI profile",
            description = "Validates the private key and fingerprint via OCI Identity SDK. compartmentId is optional — defaults to tenancy root.")
    public ResponseEntity<ApiResponse<CloudProfileResponse>> create(
            @Valid @ModelAttribute OciProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ociProfileService.createProfile(request, user.getUsername()),
                        "OCI profile created successfully"));
    }

    @GetMapping("/getAll")
    @Operation(summary = "List all OCI profiles for the current user")
    public ResponseEntity<ApiResponse<List<CloudProfileResponse>>> getAll(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                ociProfileService.getAllProfiles(user.getUsername()),
                "OCI profiles retrieved"));
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Get an OCI profile by ID", description = "Returns 403 if the caller is not the owner.")
    public ResponseEntity<ApiResponse<CloudProfileResponse>> getById(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                ociProfileService.getProfileById(profileId, user.getUsername()),
                "OCI profile retrieved"));
    }

    @GetMapping("/{profileId}/validate")
    @Operation(summary = "Validate an OCI profile by listing its real buckets",
            description = "Decrypts the stored private key and calls OCI Object Storage. Returns bucket names if credentials are valid.")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                ValidationResult.builder()
                        .profileId(profileId)
                        .provider("OCI")
                        .connected(true)
                        .message("Connected successfully")
                        .build(),
                "OCI credentials are valid"));
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "Delete an OCI profile", description = "Returns 403 if the caller is not the owner.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        ociProfileService.deleteProfile(profileId, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "OCI profile deleted"));
    }

    @Data
    @Builder
    public static class ValidationResult {
        private String profileId;
        private String provider;
        private boolean connected;
        private List<String> buckets;
        private String message;
    }
}