package com.multicloud.resourcemanagementservice.service;

import com.multicloud.resourcemanagementservice.dto.ResourceNode;

public interface ResourceFetcher {
    boolean supports(String provider);
    ResourceNode fetchResources(String profileId);
}
