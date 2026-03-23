package com.multicloud.cloudprofileservice.service;

import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofile.dto.response.CloudProfileResponse;

import java.util.List;
import java.util.UUID;

public interface CloudProfileService {

    CloudProfileResponse createGcpProfile(GcpProfileRequest request, String userId);

    CloudProfileResponse createOciProfile(OciProfileRequest request, String userId);

    CloudProfileResponse getById(UUID id, String userId);

    List<CloudProfileResponse> getAllByUser(String userId);

    CloudProfileResponse revalidate(UUID id, String userId);

    void delete(UUID id, String userId);
}