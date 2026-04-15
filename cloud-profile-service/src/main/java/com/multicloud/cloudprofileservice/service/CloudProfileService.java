package com.multicloud.cloudprofileservice.service;

import com.multicloud.cloudprofileservice.dto.response.AllCloudProfilesResponse;

public interface CloudProfileService {
    AllCloudProfilesResponse getAllCloudProfiles(String ownerId);
}
