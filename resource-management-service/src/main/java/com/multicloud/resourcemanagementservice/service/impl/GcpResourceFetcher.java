package com.multicloud.resourcemanagementservice.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.*;
import com.google.cloud.resourcemanager.v3.Project;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.cloud.resourcemanager.v3.ProjectsSettings;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.multicloud.resourcemanagementservice.client.CloudProfileServiceClient;
import com.multicloud.resourcemanagementservice.client.dto.GcpProfileDetailsResponse;
import com.multicloud.resourcemanagementservice.dto.ResourceNode;
import com.multicloud.resourcemanagementservice.dto.ResourceStats;
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

    /**
     * Fixed-depth hierarchy enforced for every GCP tree:
     * PROVIDER → PROJECT → VPC → SUBNET → RESOURCE (leaf)
     */
    private static final Map<String, String> HIERARCHY = Map.of(
            "PROVIDER", "PROJECT",
            "PROJECT", "VPC",
            "VPC", "SUBNET",
            "SUBNET", "RESOURCE");

    @Override
    public boolean supports(String provider) {
        return "GCP".equalsIgnoreCase(provider);
    }

    @Override
    public ResourceStats fetchStats(String profileId) {
        GcpProfileDetailsResponse details = profileClient.getGcpProfileDetails(profileId).getData();
        String key = details.getDecryptedServiceAccountKey();

        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));

            List<String> projectIds = discoverProjects(credentials, details.getProjectId());
            int totalVpc = 0, totalSubnet = 0, totalCompute = 0, totalStorage = 0, totalDisk = 0;

            for (String projectId : projectIds) {
                Map<String, Integer> counts = countProjectResources(projectId, credentials);
                totalVpc += counts.getOrDefault("VPC", 0);
                totalSubnet += counts.getOrDefault("SUBNET", 0);
                totalCompute += counts.getOrDefault("INSTANCE", 0);
                totalStorage += counts.getOrDefault("BUCKET", 0);
                totalDisk += counts.getOrDefault("DISK", 0);
            }

            return ResourceStats.builder()
                    .provider("GCP")
                    .projectCount(projectIds.size())
                    .vpcCount(totalVpc)
                    .subnetCount(totalSubnet)
                    .computeCount(totalCompute)
                    .storageCount(totalStorage)
                    .diskCount(totalDisk)
                    .build();

        } catch (IOException e) {
            log.error("Failed to fetch GCP stats: {}", e.getMessage());
            throw new RuntimeException("GCP Stats Fetch Failed", e);
        }
    }

    @Override
    public ResourceNode fetchResources(String profileId) {
        GcpProfileDetailsResponse details = profileClient.getGcpProfileDetails(profileId).getData();
        String key = details.getDecryptedServiceAccountKey();

        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));

            ResourceNode rootNode = ResourceNode.builder()
                    .id("gcp-root")
                    .name("Google Cloud Platform")
                    .type("PROVIDER")
                    .details(Map.of("serviceAccount", details.getServiceAccountEmail()))
                    .build();

            List<String> projectIds = discoverProjects(credentials, details.getProjectId());
            for (String projectId : projectIds) {
                ResourceNode projectNode = ResourceNode.builder()
                        .id(projectId)
                        .name(projectId)
                        .type("PROJECT")
                        .build();
                rootNode.addChild(projectNode);

                // Perform deep recursive discovery for the project
                projectNode.setChildren(fetchDeepProjectChildren(projectId, credentials));

                // Fetch high-level statistics for this project
                Map<String, Integer> counts = countProjectResources(projectId, credentials);
                projectNode.setCount(counts.values().stream().mapToInt(Integer::intValue).sum());
                projectNode.setResourceCounts(counts);
            }

            // Enforce hierarchy skeleton (placeholders) and compute all counts
            rootNode.ensureHierarchy(HIERARCHY);
            rootNode.computeCounts();

            return rootNode;

        } catch (IOException e) {
            log.error("Failed to perform deep GCP discovery: {}", e.getMessage());
            throw new RuntimeException("GCP Discovery Failed", e);
        }
    }

    private List<ResourceNode> fetchDeepProjectChildren(String projectId, GoogleCredentials credentials) {
        List<ResourceNode> children = new ArrayList<>();
        try {
            NetworksSettings networksSettings = NetworksSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials).build();
            try (NetworksClient networksClient = NetworksClient.create(networksSettings)) {
                networksClient.list(projectId).iterateAll().forEach(network -> {
                    ResourceNode vpcNode = ResourceNode.builder()
                            .id(network.getSelfLink())
                            .name(network.getName())
                            .type("VPC")
                            .details(Map.of("id", String.valueOf(network.getId())))
                            .build();
                    vpcNode.setChildren(fetchDeepVcnChildren(network.getSelfLink(), projectId, credentials));
                    children.add(vpcNode);
                });
            }

            // Synthetic VPCs
            ResourceNode storageVpc = ResourceNode.builder()
                    .id(projectId + "-gcs-vpc")
                    .name("Cloud Storage")
                    .type("VPC")
                    .details(Map.of("synthetic", true))
                    .build();
            storageVpc.setChildren(fetchDeepVcnChildren(storageVpc.getId(), projectId, credentials));
            if (!storageVpc.getChildren().isEmpty())
                children.add(storageVpc);

            ResourceNode diskVpc = ResourceNode.builder()
                    .id(projectId + "-disks-vpc")
                    .name("Persistent Disks")
                    .type("VPC")
                    .details(Map.of("synthetic", true))
                    .build();
            diskVpc.setChildren(fetchDeepVcnChildren(diskVpc.getId(), projectId, credentials));
            if (!diskVpc.getChildren().isEmpty())
                children.add(diskVpc);

        } catch (Exception e) {
            log.error("Error building deep GCP project tree: {}", e.getMessage());
        }
        return children;
    }

    private List<ResourceNode> fetchDeepVcnChildren(String vpcId, String projectId, GoogleCredentials credentials) {
        List<ResourceNode> subnets = new ArrayList<>();
        try {
            if (vpcId.endsWith("-gcs-vpc")) {
                subnets.add(ResourceNode.builder()
                        .id(projectId + "-gcs-subnet")
                        .name("GCS Buckets")
                        .type("SUBNET")
                        .details(Map.of("synthetic", true))
                        .build());
            } else if (vpcId.endsWith("-disks-vpc")) {
                DisksSettings disksSettings = DisksSettings.newBuilder().setCredentialsProvider(() -> credentials)
                        .build();
                try (DisksClient disksClient = DisksClient.create(disksSettings)) {
                    disksClient.aggregatedList(projectId).iterateAll().forEach(entry -> {
                        if (entry.getValue().getDisksList().size() > 0) {
                            String zoneName = entry.getKey().replace("zones/", "");
                            subnets.add(ResourceNode.builder()
                                    .id(projectId + "-disk-subnet-" + zoneName)
                                    .name(zoneName + " Zone")
                                    .type("SUBNET")
                                    .details(Map.of("synthetic", true, "zone", zoneName))
                                    .build());
                        }
                    });
                }
            } else {
                SubnetworksSettings subnetSettings = SubnetworksSettings.newBuilder()
                        .setCredentialsProvider(() -> credentials).build();
                try (SubnetworksClient subnetworksClient = SubnetworksClient.create(subnetSettings)) {
                    subnetworksClient.aggregatedList(projectId).iterateAll().forEach(entry -> {
                        entry.getValue().getSubnetworksList().forEach(subnet -> {
                            if (vpcId.equals(subnet.getNetwork())) {
                                ResourceNode subnetNode = ResourceNode.builder()
                                        .id(subnet.getSelfLink())
                                        .name(subnet.getName())
                                        .type("SUBNET")
                                        .details(Map.of("region", subnet.getRegion()))
                                        .build();
                                // Recursively fetch instances in this subnet
                                subnetNode
                                        .setChildren(fetchSubnetChildren(subnet.getSelfLink(), projectId, credentials));
                                subnets.add(subnetNode);
                            }
                        });
                    });
                }
            }
        } catch (Exception e) {
            log.error("Error building deep GCP VPC tree: {}", e.getMessage());
        }
        return subnets;
    }

    private List<ResourceNode> fetchSubnetChildren(String subnetId, String projectId, GoogleCredentials credentials) {
        List<ResourceNode> children = new ArrayList<>();
        try {
            if (subnetId.endsWith("-gcs-subnet")) {
                Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(credentials)
                        .build().getService();
                storage.list().iterateAll().forEach(bucket -> {
                    children.add(ResourceNode.builder()
                            .id(bucket.getName()).name(bucket.getName()).type("BUCKET").build());
                });
            } else if (subnetId.contains("-disk-subnet-")) {
                String zone = subnetId.substring(subnetId.lastIndexOf("-") + 1);
                DisksSettings disksSettings = DisksSettings.newBuilder().setCredentialsProvider(() -> credentials)
                        .build();
                try (DisksClient disksClient = DisksClient.create(disksSettings)) {
                    disksClient.list(projectId, zone).iterateAll().forEach(disk -> {
                        children.add(ResourceNode.builder()
                                .id(disk.getSelfLink()).name(disk.getName()).type("DISK").build());
                    });
                }
            } else {
                InstancesSettings instancesSettings = InstancesSettings.newBuilder()
                        .setCredentialsProvider(() -> credentials).build();
                try (InstancesClient instancesClient = InstancesClient.create(instancesSettings)) {
                    instancesClient.aggregatedList(projectId).iterateAll().forEach(entry -> {
                        entry.getValue().getInstancesList().forEach(instance -> {
                            if (!instance.getNetworkInterfacesList().isEmpty()
                                    && subnetId.equals(instance.getNetworkInterfaces(0).getSubnetwork())) {
                                children.add(ResourceNode.builder()
                                        .id(instance.getSelfLink()).name(instance.getName()).type("INSTANCE").build());
                            }
                        });
                    });
                }
            }
        } catch (Exception e) {
            log.error("Error fetching GCP subnet children: {}", e.getMessage());
        }
        return children;
    }

    private Map<String, Integer> countProjectResources(String projectId, GoogleCredentials credentials) {
        Map<String, Integer> counts = new HashMap<>();
        try {
            NetworksSettings networksSettings = NetworksSettings.newBuilder().setCredentialsProvider(() -> credentials)
                    .build();
            try (NetworksClient networksClient = NetworksClient.create(networksSettings)) {
                int vpcCount = 0;
                for (Network n : networksClient.list(projectId).iterateAll())
                    vpcCount++;
                counts.put("VPC", vpcCount);
            }

            SubnetworksSettings subnetworksSettings = SubnetworksSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials).build();
            try (SubnetworksClient subnetworksClient = SubnetworksClient.create(subnetworksSettings)) {
                int subnetCount = 0;
                for (Map.Entry<String, SubnetworksScopedList> entry : subnetworksClient.aggregatedList(projectId)
                        .iterateAll()) {
                    subnetCount += entry.getValue().getSubnetworksList().size();
                }
                counts.put("SUBNET", subnetCount);
            }

            InstancesSettings instancesSettings = InstancesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials).build();
            try (InstancesClient instancesClient = InstancesClient.create(instancesSettings)) {
                int instanceCount = 0;
                for (Map.Entry<String, InstancesScopedList> entry : instancesClient.aggregatedList(projectId)
                        .iterateAll()) {
                    instanceCount += entry.getValue().getInstancesList().size();
                }
                counts.put("INSTANCE", instanceCount);
            }

            Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(credentials).build()
                    .getService();
            int bucketCount = 0;
            for (Bucket b : storage.list().iterateAll())
                bucketCount++;
            if (bucketCount > 0) {
                counts.put("BUCKET", bucketCount);
                counts.put("VPC", counts.getOrDefault("VPC", 0) + 1); // Cloud Storage synthetic VPC
            }

            DisksSettings disksSettings = DisksSettings.newBuilder().setCredentialsProvider(() -> credentials).build();
            try (DisksClient disksClient = DisksClient.create(disksSettings)) {
                int diskCount = 0;
                for (Map.Entry<String, DisksScopedList> entry : disksClient.aggregatedList(projectId).iterateAll()) {
                    diskCount += entry.getValue().getDisksList().size();
                }
                if (diskCount > 0) {
                    counts.put("DISK", diskCount);
                    counts.put("VPC", counts.getOrDefault("VPC", 0) + 1); // Persistent Disks synthetic VPC
                }
            }
        } catch (Exception e) {
            log.warn("Error counting GCP resources for {}: {}", projectId, e.getMessage());
        }
        return counts;
    }

    private List<String> discoverProjects(GoogleCredentials credentials, String defaultProjectId) throws IOException {
        ProjectsSettings settings = ProjectsSettings.newBuilder().setCredentialsProvider(() -> credentials).build();
        List<String> projectIds = new ArrayList<>();
        try (ProjectsClient projectsClient = ProjectsClient.create(settings)) {
            for (Project project : projectsClient.searchProjects("").iterateAll()) {
                projectIds.add(project.getProjectId());
            }
        } catch (Exception e) {
            log.warn("Could not search projects: {}. Falling back to default.", e.getMessage());
        }
        if (projectIds.isEmpty() && defaultProjectId != null)
            projectIds.add(defaultProjectId);
        return projectIds;
    }
}
