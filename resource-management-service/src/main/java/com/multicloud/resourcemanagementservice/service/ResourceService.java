package com.multicloud.resourcemanagementservice.service;

import com.multicloud.resourcemanagementservice.client.CloudProfileServiceClient;
import com.multicloud.resourcemanagementservice.client.dto.GcpProfileDetailsResponse;
import com.multicloud.resourcemanagementservice.dto.ResourceNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final List<ResourceFetcher> fetchers;
    private final CloudProfileServiceClient profileClient;

    public ResourceNode getGcpResources(String profileId) {
        return fetchers.stream()
                .filter(f -> f.supports("GCP"))
                .findFirst()
                .map(f -> f.fetchResources(profileId))
                .orElseThrow(() -> new RuntimeException("GCP Fetcher not found"));
    }

    public ResourceNode getOciResources(String profileId) {
        return fetchers.stream()
                .filter(f -> f.supports("OCI"))
                .findFirst()
                .map(f -> f.fetchResources(profileId))
                .orElseThrow(() -> new RuntimeException("OCI Fetcher not found"));
    }
}
