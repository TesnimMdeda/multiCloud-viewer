package com.multicloud.resourcemanagementservice.service;

import com.multicloud.resourcemanagementservice.dto.ResourceNode;
import com.multicloud.resourcemanagementservice.dto.ResourceStats;

public interface ResourceFetcher {
    boolean supports(String provider);
    ResourceNode fetchResources(String profileId);
    ResourceStats fetchStats(String profileId);
}
