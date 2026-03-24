package com.multicloud.cloudprofileservice.controller;

import com.multicloud.cloudprofileservice.dto.response.ApiResponse;
import com.multicloud.cloudprofileservice.dto.response.StorageObjectResponse;
import com.multicloud.cloudprofileservice.service.CloudStorageService;
import com.multicloud.cloudprofileservice.storage.CloudStorageAdapter.StorageObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/cloud/storage")
@RequiredArgsConstructor
@Tag(name = "Cloud Storage", description = "Unified storage operations (GCS + OCI Object Storage)")
public class CloudStorageController {

    private final CloudStorageService storageService;

    // ─── LIST BUCKETS ────────────────────────────────────────────────────────

    @GetMapping("/{profileId}/buckets")
    @Operation(summary = "List all buckets for a cloud profile")
    public ResponseEntity<ApiResponse<List<String>>> listBuckets(
            @PathVariable String profileId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(ApiResponse.success(
                storageService.listBuckets(profileId, user.getUsername()),
                "Buckets retrieved"));
    }

    // ─── LIST OBJECTS ────────────────────────────────────────────────────────

    @GetMapping("/{profileId}/buckets/{bucket}/objects")
    @Operation(summary = "List objects in a bucket (optional ?prefix= filter)")
    public ResponseEntity<ApiResponse<List<StorageObjectResponse>>> listObjects(
            @PathVariable String profileId,
            @PathVariable String bucket,
            @RequestParam(required = false) String prefix,
            @AuthenticationPrincipal UserDetails user) {

        List<StorageObjectResponse> objects =
                storageService.listObjects(profileId, bucket, prefix, user.getUsername())
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(ApiResponse.success(objects, "Objects retrieved"));
    }

    // ─── UPLOAD OBJECT ───────────────────────────────────────────────────────

    @PostMapping(value = "/{profileId}/buckets/{bucket}/objects",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file to a bucket")
    public ResponseEntity<ApiResponse<Void>> uploadObject(
            @PathVariable String profileId,
            @PathVariable String bucket,
            @RequestParam String objectName,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserDetails user) throws IOException {

        storageService.uploadObject(profileId, bucket, objectName,
                file.getInputStream(), file.getSize(), user.getUsername());

        return ResponseEntity.ok(ApiResponse.success(null, "File uploaded successfully"));
    }

    // ─── DELETE OBJECT ───────────────────────────────────────────────────────

    @DeleteMapping("/{profileId}/buckets/{bucket}/objects/{objectName}")
    @Operation(summary = "Delete an object from a bucket")
    public ResponseEntity<ApiResponse<Void>> deleteObject(
            @PathVariable String profileId,
            @PathVariable String bucket,
            @PathVariable String objectName,
            @AuthenticationPrincipal UserDetails user) {

        storageService.deleteObject(profileId, bucket, objectName, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "Object deleted"));
    }

    // ─── helper ─────────────────────────────────────────────────────────────

    private StorageObjectResponse toResponse(StorageObject obj) {
        return StorageObjectResponse.builder()
                .name(obj.name())
                .size(obj.size())
                .contentType(obj.contentType())
                .lastModified(obj.lastModified())
                .build();
    }
}