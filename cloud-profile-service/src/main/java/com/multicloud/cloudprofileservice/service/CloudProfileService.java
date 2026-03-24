package com.multicloud.cloudprofileservice.service;

import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;

import java.util.List;

public interface CloudProfileService {

    CloudProfileResponse createGcpProfile(GcpProfileRequest request, String ownerId);

    CloudProfileResponse createOciProfile(OciProfileRequest request, String ownerId);

    List<CloudProfileResponse> getProfilesByOwner(String ownerId);

    void deleteProfile(String profileId, String ownerId);
}