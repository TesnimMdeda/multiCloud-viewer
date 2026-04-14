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
     * Any level without real children receives a placeholder node instead of [] or
     * null.
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
    public ResourceNode fetchResources(String profileId) {
        GcpProfileDetailsResponse details = profileClient.getGcpProfileDetails(profileId).getData();
        String key = details.getDecryptedServiceAccountKey();

        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));

            // Root node — represents the entire GCP account
            ResourceNode rootNode = ResourceNode.builder()
                    .id("gcp-root")
                    .name("Google Cloud Platform")
                    .type("PROVIDER")
                    .details(Map.of("serviceAccount", details.getServiceAccountEmail()))
                    .build();

            // ── Step 1: Discover all accessible projects ──────────────────────────────
            List<String> projectIds = discoverProjects(credentials, details.getProjectId());
            log.info("Discovered {} project(s) for GCP profile {}", projectIds.size(), profileId);

            // ── Step 2: Fetch resources for each project ──────────────────────────────
            for (String projectId : projectIds) {
                try {
                    ResourceNode projectNode = ResourceNode.builder()
                            .id(projectId)
                            .name(projectId)
                            .type("PROJECT")
                            .build();
                    rootNode.addChild(projectNode);

                    fetchComputeResources(projectId, credentials, projectNode);
                    fetchBuckets(projectId, credentials, projectNode);
                    fetchDisks(projectId, credentials, projectNode);
                } catch (Exception e) {
                    log.error("Failed to fetch resources for project {}: {}", projectId, e.getMessage());
                }
            }

            // ── Step 3: Guarantee PROVIDER → PROJECT → VPC → SUBNET → RESOURCE depth ─
            rootNode.ensureHierarchy(HIERARCHY);

            // ── Step 4: Populate count field on every node (real children only) ───────
            rootNode.computeCounts();

            return rootNode;

        } catch (IOException e) {
            log.error("Failed to initialise GCP credentials: {}", e.getMessage());
            throw new RuntimeException("GCP Discovery Failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Project discovery
    // ─────────────────────────────────────────────────────────────────────────────

    private List<String> discoverProjects(GoogleCredentials credentials,
            String defaultProjectId) throws IOException {
        ProjectsSettings settings = ProjectsSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();

        List<String> projectIds = new ArrayList<>();
        try (ProjectsClient projectsClient = ProjectsClient.create(settings)) {
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

    // ─────────────────────────────────────────────────────────────────────────────
    // Compute: VPCs → Subnets → Instances
    // ─────────────────────────────────────────────────────────────────────────────

    private void fetchComputeResources(String projectId,
            GoogleCredentials credentials,
            ResourceNode projectNode) {
        try {
            NetworksSettings networksSettings = NetworksSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials).build();
            SubnetworksSettings subnetSettings = SubnetworksSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials).build();
            InstancesSettings instancesSettings = InstancesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials).build();

            try (NetworksClient networksClient = NetworksClient.create(networksSettings);
                    SubnetworksClient subnetworksClient = SubnetworksClient.create(subnetSettings);
                    InstancesClient instancesClient = InstancesClient.create(instancesSettings)) {

                // A. Fetch VPCs ────────────────────────────────────────────────────────
                Map<String, ResourceNode> vpcNodes = new HashMap<>();
                Map<String, ResourceNode> subnetNodes = new HashMap<>();

                for (Network network : networksClient.list(projectId).iterateAll()) {
                    ResourceNode vpcNode = ResourceNode.builder()
                            .id(network.getSelfLink())
                            .name(network.getName())
                            .type("VPC")
                            .details(Map.of("id", String.valueOf(network.getId())))
                            .build();
                    vpcNodes.put(network.getSelfLink(), vpcNode);
                    projectNode.addChild(vpcNode);
                }

                // B. Fetch Subnets and attach to their VPC ────────────────────────────
                for (Map.Entry<String, SubnetworksScopedList> entry : subnetworksClient.aggregatedList(projectId)
                        .iterateAll()) {

                    for (Subnetwork subnet : entry.getValue().getSubnetworksList()) {
                        ResourceNode subnetNode = ResourceNode.builder()
                                .id(subnet.getSelfLink())
                                .name(subnet.getName())
                                .type("SUBNET")
                                .details(Map.of(
                                        "region", subnet.getRegion(),
                                        "ipCidrRange", subnet.getIpCidrRange()))
                                .build();
                        subnetNodes.put(subnet.getSelfLink(), subnetNode);

                        ResourceNode parentVpc = vpcNodes.get(subnet.getNetwork());
                        if (parentVpc != null) {
                            parentVpc.addChild(subnetNode);
                        }
                        // Subnets with no matching VPC are discarded — ensureHierarchy
                        // will add a placeholder VPC if the project has no children.
                    }
                }

                // C. Fetch Instances and route them through VPC → Subnet ───────────────
                for (Map.Entry<String, InstancesScopedList> entry : instancesClient.aggregatedList(projectId)
                        .iterateAll()) {

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

                        if (instance.getNetworkInterfacesList().isEmpty()) {
                            log.debug("Instance {} has no network interfaces — skipping", instance.getName());
                            continue;
                        }

                        String subnetLink = instance.getNetworkInterfaces(0).getSubnetwork();
                        String vpcLink = instance.getNetworkInterfaces(0).getNetwork();

                        ResourceNode parentSubnet = subnetNodes.get(subnetLink);
                        if (parentSubnet != null) {
                            // Happy path: instance → known subnet
                            parentSubnet.addChild(instanceNode);
                        } else {
                            ResourceNode parentVpc = vpcNodes.get(vpcLink);
                            if (parentVpc != null) {
                                // Instance maps to a VPC but not a specific subnet →
                                // place it in a synthetic "Default Subnet" of that VPC
                                ResourceNode defaultSubnet = getOrCreateSyntheticSubnet(
                                        parentVpc, "gcp-default-subnet", "Default Subnet");
                                defaultSubnet.addChild(instanceNode);
                            } else {
                                // No VPC match at all — skip to avoid hierarchy breakage
                                log.debug("Instance {} could not be mapped to any VPC/subnet — skipping",
                                        instance.getName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Compute resources for project {}: {}", projectId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Storage: Buckets under a synthetic "Cloud Storage" VPC → Subnet
    // ─────────────────────────────────────────────────────────────────────────────

    private void fetchBuckets(String projectId,
            GoogleCredentials credentials,
            ResourceNode projectNode) {
        try {
            Storage storage = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(credentials)
                    .build()
                    .getService();

            List<Bucket> buckets = new ArrayList<>();
            storage.list().iterateAll().forEach(buckets::add);
            if (buckets.isEmpty())
                return;

            // Synthetic VPC — represents the Cloud Storage service
            ResourceNode storageVpc = ResourceNode.builder()
                    .id(projectId + "-gcs-vpc")
                    .name("Cloud Storage")
                    .type("VPC")
                    .details(Map.of("synthetic", true))
                    .build();

            // Synthetic Subnet — groups all buckets at the correct depth
            ResourceNode bucketSubnet = ResourceNode.builder()
                    .id(projectId + "-gcs-subnet")
                    .name("GCS Buckets")
                    .type("SUBNET")
                    .details(Map.of("synthetic", true))
                    .build();

            storageVpc.addChild(bucketSubnet);
            projectNode.addChild(storageVpc);

            buckets.forEach(bucket -> bucketSubnet.addChild(ResourceNode.builder()
                    .id(bucket.getName())
                    .name(bucket.getName())
                    .type("BUCKET")
                    .details(Map.of(
                            "location", bucket.getLocation(),
                            "storageClass", bucket.getStorageClass().name(),
                            "createTime", String.valueOf(bucket.getCreateTime())))
                    .build()));

        } catch (Exception e) {
            log.warn("Failed to fetch Buckets for project {}: {}", projectId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Disks: grouped per zone under a synthetic "Persistent Disks" VPC
    // ─────────────────────────────────────────────────────────────────────────────

    private void fetchDisks(String projectId,
            GoogleCredentials credentials,
            ResourceNode projectNode) {
        try {
            DisksSettings disksSettings = DisksSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            try (DisksClient disksClient = DisksClient.create(disksSettings)) {

                // Synthetic VPC — represents the Persistent Disk service
                ResourceNode diskVpc = ResourceNode.builder()
                        .id(projectId + "-disks-vpc")
                        .name("Persistent Disks")
                        .type("VPC")
                        .details(Map.of("synthetic", true))
                        .build();

                boolean anyDisk = false;

                for (Map.Entry<String, DisksScopedList> entry : disksClient.aggregatedList(projectId).iterateAll()) {

                    for (Disk disk : entry.getValue().getDisksList()) {
                        if (!anyDisk) {
                            // Add the synthetic VPC only when at least one disk exists
                            projectNode.addChild(diskVpc);
                            anyDisk = true;
                        }

                        // Group disks into per-zone synthetic Subnets
                        String zoneName = entry.getKey().replace("zones/", "");
                        ResourceNode zoneSubnet = getOrCreateSyntheticSubnet(
                                diskVpc, zoneName, zoneName + " Zone");

                        zoneSubnet.addChild(ResourceNode.builder()
                                .id(disk.getSelfLink())
                                .name(disk.getName())
                                .type("DISK")
                                .details(Map.of(
                                        "status", disk.getStatus(),
                                        "sizeGb", disk.getSizeGb(),
                                        "type", disk.getType(),
                                        "zone", zoneName))
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Disks for project {}: {}", projectId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns an existing SUBNET child of {@code parentVpc} with the given id, or
     * creates
     * and attaches a new one. Used to avoid duplicate synthetic subnet nodes.
     */
    private ResourceNode getOrCreateSyntheticSubnet(ResourceNode parentVpc,
            String subnetId,
            String subnetName) {
        return parentVpc.getChildren().stream()
                .filter(c -> "SUBNET".equals(c.getType()) && subnetId.equals(c.getId()))
                .findFirst()
                .orElseGet(() -> {
                    ResourceNode sub = ResourceNode.builder()
                            .id(subnetId)
                            .name(subnetName)
                            .type("SUBNET")
                            .details(Map.of("synthetic", true))
                            .build();
                    parentVpc.addChild(sub);
                    return sub;
                });
    }
}
