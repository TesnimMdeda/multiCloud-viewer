package com.multicloud.resourcemanagementservice.service;

import com.multicloud.resourcemanagementservice.client.CloudProfileServiceClient;
import com.multicloud.resourcemanagementservice.client.dto.GcpProfileDetailsResponse;
import com.multicloud.resourcemanagementservice.dto.ResourceNode;
import com.multicloud.resourcemanagementservice.dto.ResourceStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final List<ResourceFetcher> fetchers;
    private final CloudProfileServiceClient profileClient;

    @Cacheable(value = "gcpResources", key = "#profileId")
    public ResourceNode getGcpResources(String profileId) {
        log.info("Cache MISS — fetching GCP resources for profile: {}", profileId);
        return fetchers.stream()
                .filter(f -> f.supports("GCP"))
                .findFirst()
                .map(f -> f.fetchResources(profileId))
                .orElseThrow(() -> new RuntimeException("GCP Fetcher not found"));
    }

    @Cacheable(value = "ociResources", key = "#profileId")
    public ResourceNode getOciResources(String profileId) {
        log.info("Cache MISS — fetching OCI resources for profile: {}", profileId);
        return fetchers.stream()
                .filter(f -> f.supports("OCI"))
                .findFirst()
                .map(f -> f.fetchResources(profileId))
                .orElseThrow(() -> new RuntimeException("OCI Fetcher not found"));
    }

    @Cacheable(value = "resourceStats", key = "#profileId")
    public ResourceStats getStats(String profileId) {
        log.info("Cache MISS — fetching stats for profile: {}", profileId);
        String provider = profileClient.getProfile(profileId).getData().getProvider();
        return fetchers.stream()
                .filter(f -> f.supports(provider))
                .findFirst()
                .map(f -> f.fetchStats(profileId))
                .orElseThrow(() -> new RuntimeException(provider + " Fetcher not found"));
    }
}
