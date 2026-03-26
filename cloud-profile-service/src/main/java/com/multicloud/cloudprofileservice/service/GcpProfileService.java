package com.multicloud.cloudprofileservice.service;

import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;

import java.util.List;

public interface GcpProfileService {

    CloudProfileResponse createProfile(GcpProfileRequest request, String ownerId);

    List<CloudProfileResponse> getAllProfiles(String ownerId);

    CloudProfileResponse getProfileById(String profileId, String ownerId);

    void deleteProfile(String profileId, String ownerId);
}