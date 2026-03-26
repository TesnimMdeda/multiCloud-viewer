package com.multicloud.cloudprofileservice.service;

import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;

import java.util.List;

public interface OciProfileService {

    CloudProfileResponse createProfile(OciProfileRequest request, String ownerId);

    List<CloudProfileResponse> getAllProfiles(String ownerId);

    CloudProfileResponse getProfileById(String profileId, String ownerId);

    void deleteProfile(String profileId, String ownerId);
}