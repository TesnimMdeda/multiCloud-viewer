package com.multicloud.resourcemanagementservice.service.impl;

import com.google.cloud.resourcemanager.v3.Project;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.cloud.resourcemanager.v3.ProjectsSettings;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.*;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.multicloud.resourcemanagementservice.client.CloudProfileServiceClient;
import com.multicloud.resourcemanagementservice.client.dto.GcpProfileDetailsResponse;
import com.multicloud.resourcemanagementservice.dto.ResourceNode;
import com.multicloud.resourcemanagementservice.service.ResourceFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GcpResourceFetcher implements ResourceFetcher {

    private final CloudProfileServiceClient profileClient;

    @Override
    public boolean supports(String provider) {
        return "GCP".equalsIgnoreCase(provider);
    }

    @Override
    public ResourceNode fetchResources(String profileId) {
        GcpProfileDetailsResponse details = profileClient.getGcpProfileDetails(profileId).getData();
        String key = details.getDecryptedServiceAccountKey();

        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));

            // Root node for the entire GCP Infrastructure
            ResourceNode rootNode = ResourceNode.builder()
                    .id("gcp-root")
                    .name("Google Cloud Platform")
                    .type("PROVIDER")
                    .details(Map.of("serviceAccount", details.getServiceAccountEmail()))
                    .build();

            // 1. Discover all accessible projects
            List<String> projectIds = discoverProjects(credentials, details.getProjectId());
            log.info("Discovered {} projects for GCP profile {}", projectIds.size(), profileId);

            // 2. Fetch resources for each project
            for (String projectId : projectIds) {
                try {
                    ResourceNode projectNode = ResourceNode.builder()
                            .id(projectId)
                            .name(projectId)
                            .type("PROJECT")
                            .build();

                    // Add project node immediately so it appears even if sub-resources are forbidden
                    rootNode.addChild(projectNode);

                    fetchBuckets(projectId, credentials, projectNode);
                    fetchComputeResources(projectId, credentials, projectNode);
                    fetchDisks(projectId, credentials, projectNode);
                } catch (Exception e) {
                    log.error("Failed to fetch resources for project {}: {}", projectId, e.getMessage());
                }
            }

            return rootNode;

        } catch (IOException e) {
            log.error("Failed to initialize GCP credentials: {}", e.getMessage());
            throw new RuntimeException("GCP Discovery Failed", e);
        }
    }

    private List<String> discoverProjects(GoogleCredentials credentials, String defaultProjectId) throws IOException {
        ProjectsSettings settings = ProjectsSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();

        List<String> projectIds = new ArrayList<>();
        try (ProjectsClient projectsClient = ProjectsClient.create(settings)) {
            // Search for projects where the credentials have access
            for (Project project : projectsClient.searchProjects("").iterateAll()) {
                projectIds.add(project.getProjectId());
            }
        } catch (Exception e) {
            log.warn("Could not search projects: {}. Falling back to profile project.", e.getMessage());
        }
        
        if (projectIds.isEmpty() && defaultProjectId != null) {
            projectIds.add(defaultProjectId);
        }
        
        return projectIds;
    }

    private void fetchBuckets(String projectId, GoogleCredentials credentials, ResourceNode projectNode) {
        try {
            Storage storage = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(credentials)
                    .build()
                    .getService();

            com.google.api.gax.paging.Page<Bucket> buckets = storage.list();
            for (Bucket bucket : buckets.iterateAll()) {
                projectNode.addChild(ResourceNode.builder()
                        .id(bucket.getName())
                        .name(bucket.getName())
                        .type("BUCKET")
                        .details(Map.of(
                                "location", bucket.getLocation(),
                                "storageClass", bucket.getStorageClass().name(),
                                "createTime", String.valueOf(bucket.getCreateTime())
                        ))
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Buckets for project {}: {}", projectId, e.getMessage());
        }
    }

    private void fetchComputeResources(String projectId, GoogleCredentials credentials, ResourceNode projectNode) {
        try {
            NetworksSettings networksSettings = NetworksSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            
            try (NetworksClient networksClient = NetworksClient.create(networksSettings);
                 SubnetworksClient subnetworksClient = SubnetworksClient.create(SubnetworksSettings.newBuilder().setCredentialsProvider(() -> credentials).build());
                 InstancesClient instancesClient = InstancesClient.create(InstancesSettings.newBuilder().setCredentialsProvider(() -> credentials).build())) {

                // A. Fetch VPCs
                Map<String, ResourceNode> vpcNodes = new HashMap<>();
                NetworksClient.ListPagedResponse networks = networksClient.list(projectId);
                for (Network network : networks.iterateAll()) {
                    ResourceNode vpcNode = ResourceNode.builder()
                            .id(network.getSelfLink())
                            .name(network.getName())
                            .type("VPC")
                            .details(Map.of("id", String.valueOf(network.getId())))
                            .build();
                    vpcNodes.put(network.getSelfLink(), vpcNode);
                    projectNode.addChild(vpcNode);
                }

                // B. Fetch Subnets and Group by VPC
                Map<String, ResourceNode> subnetNodes = new HashMap<>();
                SubnetworksClient.AggregatedListPagedResponse subnets = subnetworksClient.aggregatedList(projectId);
                for (Map.Entry<String, SubnetworksScopedList> entry : subnets.iterateAll()) {
                    for (Subnetwork subnet : entry.getValue().getSubnetworksList()) {
                        ResourceNode subnetNode = ResourceNode.builder()
                                .id(subnet.getSelfLink())
                                .name(subnet.getName())
                                .type("SUBNET")
                                .details(Map.of(
                                        "region", subnet.getRegion(),
                                        "ipCidrRange", subnet.getIpCidrRange()
                                ))
                                .build();
                        subnetNodes.put(subnet.getSelfLink(), subnetNode);
                        
                        ResourceNode parentVpc = vpcNodes.get(subnet.getNetwork());
                        if (parentVpc != null) {
                            parentVpc.addChild(subnetNode);
                        }
                    }
                }

                // C. Fetch Instances and Group by Subnet/VPC
                InstancesClient.AggregatedListPagedResponse instances = instancesClient.aggregatedList(projectId);
                for (Map.Entry<String, InstancesScopedList> entry : instances.iterateAll()) {
                    for (Instance instance : entry.getValue().getInstancesList()) {
                        Map<String, Object> details = new HashMap<>();
                        details.put("status", instance.getStatus());
                        details.put("machineType", instance.getMachineType());
                        details.put("zone", entry.getKey().replace("zones/", ""));

                        ResourceNode instanceNode = ResourceNode.builder()
                                .id(instance.getSelfLink())
                                .name(instance.getName())
                                .type("INSTANCE")
                                .details(details)
                                .build();

                        // Map to subnet
                        if (!instance.getNetworkInterfacesList().isEmpty()) {
                            String subnetLink = instance.getNetworkInterfaces(0).getSubnetwork();
                            ResourceNode parentSubnet = subnetNodes.get(subnetLink);
                            if (parentSubnet != null) {
                                parentSubnet.addChild(instanceNode);
                            } else {
                                String vpcLink = instance.getNetworkInterfaces(0).getNetwork();
                                ResourceNode parentVpc = vpcNodes.get(vpcLink);
                                if (parentVpc != null) {
                                    parentVpc.addChild(instanceNode);
                                } else {
                                    projectNode.addChild(instanceNode);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Compute resources for project {}: {}", projectId, e.getMessage());
        }
    }

    private void fetchDisks(String projectId, GoogleCredentials credentials, ResourceNode projectNode) {
        try {
            DisksSettings disksSettings = DisksSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            
            try (DisksClient disksClient = DisksClient.create(disksSettings)) {
                DisksClient.AggregatedListPagedResponse disks = disksClient.aggregatedList(projectId);
                for (Map.Entry<String, DisksScopedList> entry : disks.iterateAll()) {
                    for (Disk disk : entry.getValue().getDisksList()) {
                        projectNode.addChild(ResourceNode.builder()
                                .id(disk.getSelfLink())
                                .name(disk.getName())
                                .type("DISK")
                                .details(Map.of(
                                        "status", disk.getStatus(),
                                        "sizeGb", disk.getSizeGb(),
                                        "type", disk.getType(),
                                        "zone", entry.getKey().replace("zones/", "")
                                ))
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Disks for project {}: {}", projectId, e.getMessage());
        }
    }
}
