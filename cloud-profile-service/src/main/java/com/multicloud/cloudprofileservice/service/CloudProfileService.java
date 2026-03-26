package com.multicloud.cloudprofileservice.service;

import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;

import java.util.List;

public interface CloudProfileService {

    // ─── CREATE ─────────────────────────────────────────────────────────────

    CloudProfileResponse createGcpProfile(GcpProfileRequest request, String ownerId);

    CloudProfileResponse createOciProfile(OciProfileRequest request, String ownerId);

    // ─── LIST ALL (owned by current user) ───────────────────────────────────

    /** All profiles regardless of provider, owned by the given user. */
    List<CloudProfileResponse> getProfilesByOwner(String ownerId);

    /** All GCP profiles owned by the given user. */
    List<CloudProfileResponse> getGcpProfilesByOwner(String ownerId);

    /** All OCI profiles owned by the given user. */
    List<CloudProfileResponse> getOciProfilesByOwner(String ownerId);

    // ─── GET BY ID ───────────────────────────────────────────────────────────

    /** Fetch any profile by ID (owner enforced). */
    CloudProfileResponse getProfileById(String profileId, String ownerId);

    /** Fetch a GCP profile by ID (owner enforced). */
    CloudProfileResponse getGcpProfileById(String profileId, String ownerId);

    /** Fetch an OCI profile by ID (owner enforced). */
    CloudProfileResponse getOciProfileById(String profileId, String ownerId);

    // ─── DELETE ──────────────────────────────────────────────────────────────

    /** Delete any profile by ID (owner enforced). */
    void deleteProfile(String profileId, String ownerId);

    /** Delete a GCP profile by ID (owner enforced). */
    void deleteGcpProfile(String profileId, String ownerId);

    /** Delete an OCI profile by ID (owner enforced). */
    void deleteOciProfile(String profileId, String ownerId);
}