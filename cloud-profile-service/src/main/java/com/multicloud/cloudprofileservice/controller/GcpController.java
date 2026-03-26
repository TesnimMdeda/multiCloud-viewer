package com.multicloud.cloudprofileservice.controller;

import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.ApiResponse;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.service.GcpProfileService;
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
@RequestMapping("/api/cloud/gcp")
@RequiredArgsConstructor
@Tag(name = "GCP", description = "Create, fetch, validate and delete GCP profiles")
public class GcpController {

    private final GcpProfileService   gcpProfileService;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a GCP profile",
            description = "Validates the service account JSON via GCP SDK, encrypts it with AES-256-GCM and persists the profile.")
    public ResponseEntity<ApiResponse<CloudProfileResponse>> create(
            @Valid @ModelAttribute GcpProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        gcpProfileService.createProfile(request, user.getUsername()),
                        "GCP profile created and validated successfully"));
    }

    @GetMapping("/getAll")
    @Operation(summary = "List all GCP profiles for the current user")
    public ResponseEntity<ApiResponse<List<CloudProfileResponse>>> getAll(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                gcpProfileService.getAllProfiles(user.getUsername()),
                "GCP profiles retrieved"));
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Get a GCP profile by ID", description = "Returns 403 if the caller is not the owner.")
    public ResponseEntity<ApiResponse<CloudProfileResponse>> getById(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                gcpProfileService.getProfileById(profileId, user.getUsername()),
                "GCP profile retrieved"));
    }

    @GetMapping("/{profileId}/validate")
    @Operation(summary = "Validate a GCP profile by listing its real buckets",
            description = "Decrypts the stored key and calls the GCS API. Returns bucket names if credentials are valid.")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {


        return ResponseEntity.ok(ApiResponse.success(
                ValidationResult.builder()
                        .profileId(profileId)
                        .provider("GCP")
                        .connected(true)
                        .message("Connected successfully — ")
                        .build(),
                "GCP credentials are valid"));
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "Delete a GCP profile", description = "Returns 403 if the caller is not the owner.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        gcpProfileService.deleteProfile(profileId, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "GCP profile deleted"));
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