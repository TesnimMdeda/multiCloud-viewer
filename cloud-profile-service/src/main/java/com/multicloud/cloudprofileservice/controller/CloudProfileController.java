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
@Tag(name = "Cloud Profiles", description = "Manage GCP and OCI cloud provider credentials")
public class CloudProfileController {

    private final CloudProfileService profileService;

    // ════════════════════════════════════════════════════════════════
    // GCP
    // ════════════════════════════════════════════════════════════════

    @PostMapping(value = "/gcp/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "createGcpProfile",
            summary     = "createGcpProfile",
            description = "Validates the service account JSON key against the GCP SDK, "
                    + "encrypts it with AES-256-GCM and persists the profile. "
                    + "Returns the created profile with ownerId set to the authenticated user."
    )
    public ResponseEntity<ApiResponse<CloudProfileResponse>> createGcpProfile(
            @Valid @ModelAttribute GcpProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        profileService.createGcpProfile(request, user.getUsername()),
                        "GCP profile created and validated successfully"));
    }

    @GetMapping("/gcp/getAll")
    @Operation(
            operationId = "getAllGcpProfiles",
            summary     = "getAllGcpProfiles",
            description = "Returns the list of all GCP profiles owned by the authenticated user."
    )
    public ResponseEntity<ApiResponse<List<CloudProfileResponse>>> getAllGcpProfiles(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                profileService.getGcpProfilesByOwner(user.getUsername()),
                "GCP profiles retrieved"));
    }

    @GetMapping("/gcp/getById/{profileId}")
    @Operation(
            operationId = "getGcpProfileById",
            summary     = "getGcpProfileById",
            description = "Returns a single GCP profile by its ID. Returns 403 if the caller is not the owner."
    )
    public ResponseEntity<ApiResponse<CloudProfileResponse>> getGcpProfileById(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                profileService.getGcpProfileById(profileId, user.getUsername()),
                "GCP profile retrieved"));
    }

    @DeleteMapping("/gcp/delete/{profileId}")
    @Operation(
            operationId = "deleteGcpProfileById",
            summary     = "deleteGcpProfileById",
            description = "Permanently deletes the GCP profile and its AES-encrypted service account key. "
                    + "Returns 403 if the caller is not the owner."
    )
    public ResponseEntity<ApiResponse<Void>> deleteGcpProfileById(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        profileService.deleteGcpProfile(profileId, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "GCP profile deleted"));
    }

    // ════════════════════════════════════════════════════════════════
    // OCI
    // ════════════════════════════════════════════════════════════════

    @PostMapping(value = "/oci/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "createOciProfile",
            summary     = "createOciProfile",
            description = "Validates the private key (.pem) and fingerprint against the OCI Identity SDK. "
                    + "compartmentId is optional — omit it to use the root compartment (tenancyOcid). "
                    + "The private key is encrypted with AES-256-GCM before storage."
    )
    public ResponseEntity<ApiResponse<CloudProfileResponse>> createOciProfile(
            @Valid @ModelAttribute OciProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        profileService.createOciProfile(request, user.getUsername()),
                        "OCI profile created successfully"));
    }

    @GetMapping("/oci/getAll")
    @Operation(
            operationId = "getAllOciProfiles",
            summary     = "getAllOciProfiles",
            description = "Returns the list of all OCI profiles owned by the authenticated user."
    )
    public ResponseEntity<ApiResponse<List<CloudProfileResponse>>> getAllOciProfiles(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                profileService.getOciProfilesByOwner(user.getUsername()),
                "OCI profiles retrieved"));
    }

    @GetMapping("/oci/getById/{profileId}")
    @Operation(
            operationId = "getOciProfileById",
            summary     = "getOciProfileById",
            description = "Returns a single OCI profile by its ID. Returns 403 if the caller is not the owner."
    )
    public ResponseEntity<ApiResponse<CloudProfileResponse>> getOciProfileById(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                profileService.getOciProfileById(profileId, user.getUsername()),
                "OCI profile retrieved"));
    }

    @DeleteMapping("/oci/delete/{profileId}")
    @Operation(
            operationId = "deleteOciProfileById",
            summary     = "deleteOciProfileById",
            description = "Permanently deletes the OCI profile and its AES-encrypted private key. "
                    + "Returns 403 if the caller is not the owner."
    )
    public ResponseEntity<ApiResponse<Void>> deleteOciProfileById(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        profileService.deleteOciProfile(profileId, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "OCI profile deleted"));
    }

    // ════════════════════════════════════════════════════════════════
    // SHARED — any provider
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/getAll")
    @Operation(
            operationId = "getAllProfiles",
            summary     = "getAllProfiles",
            description = "Returns every GCP and OCI profile owned by the authenticated user in a single list."
    )
    public ResponseEntity<ApiResponse<List<CloudProfileResponse>>> getAllProfiles(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                profileService.getProfilesByOwner(user.getUsername()),
                "Profiles retrieved"));
    }

    @GetMapping("/getById/{profileId}")
    @Operation(
            operationId = "getProfileById",
            summary     = "getProfileById",
            description = "Returns a single profile by ID regardless of provider (GCP or OCI). "
                    + "Returns 403 if the caller is not the owner."
    )
    public ResponseEntity<ApiResponse<CloudProfileResponse>> getProfileById(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                profileService.getProfileById(profileId, user.getUsername()),
                "Profile retrieved"));
    }

    @DeleteMapping("/delete/{profileId}")
    @Operation(
            operationId = "deleteProfileById",
            summary     = "deleteProfileById",
            description = "Deletes any profile by ID regardless of provider (GCP or OCI). "
                    + "Returns 403 if the caller is not the owner."
    )
    public ResponseEntity<ApiResponse<Void>> deleteProfileById(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        profileService.deleteProfile(profileId, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "Profile deleted"));
    }
}