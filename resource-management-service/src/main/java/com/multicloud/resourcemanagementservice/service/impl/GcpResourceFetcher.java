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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

            // Use a shared thread pool for parallel operations across all projects
            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(6, Runtime.getRuntime().availableProcessors()));

            try {
                for (String projectId : projectIds) {
                    ResourceNode projectNode = buildProjectTree(projectId, credentials, executor);
                    rootNode.addChild(projectNode);
                }
            } finally {
                executor.shutdown();
            }

            // Enforce hierarchy skeleton (placeholders) and compute all counts from the tree
            // — NO separate countProjectResources() call needed
            rootNode.ensureHierarchy(HIERARCHY);
            rootNode.computeCounts();

            return rootNode;

        } catch (IOException e) {
            log.error("Failed to perform deep GCP discovery: {}", e.getMessage());
            throw new RuntimeException("GCP Discovery Failed", e);
        }
    }

    /**
     * Builds the full project tree with a single set of gRPC clients, pre-fetching
     * all instances once and distributing them to subnets by reference.
     */
    private ResourceNode buildProjectTree(String projectId, GoogleCredentials credentials,
            ExecutorService executor) {
        ResourceNode projectNode = ResourceNode.builder()
                .id(projectId)
                .name(projectId)
                .type("PROJECT")
                .build();

        try (
                NetworksClient networksClient = NetworksClient.create(
                        NetworksSettings.newBuilder().setCredentialsProvider(() -> credentials).build());
                SubnetworksClient subnetworksClient = SubnetworksClient.create(
                        SubnetworksSettings.newBuilder().setCredentialsProvider(() -> credentials).build());
                InstancesClient instancesClient = InstancesClient.create(
                        InstancesSettings.newBuilder().setCredentialsProvider(() -> credentials).build());
                DisksClient disksClient = DisksClient.create(
                        DisksSettings.newBuilder().setCredentialsProvider(() -> credentials).build())) {

            Storage storage = StorageOptions.newBuilder()
                    .setProjectId(projectId).setCredentials(credentials).build().getService();

            // ── PRE-FETCH: Build instance-to-subnet map ONCE for the entire project ──
            Map<String, List<Instance>> instancesBySubnet = new HashMap<>();
            instancesClient.aggregatedList(projectId).iterateAll().forEach(entry ->
                    entry.getValue().getInstancesList().forEach(instance -> {
                        if (!instance.getNetworkInterfacesList().isEmpty()) {
                            String subnetSelfLink = instance.getNetworkInterfaces(0).getSubnetwork();
                            instancesBySubnet.computeIfAbsent(subnetSelfLink, k -> new ArrayList<>())
                                    .add(instance);
                        }
                    }));

            // ── PRE-FETCH: Build subnet-to-VPC map ONCE ──
            Map<String, List<Subnetwork>> subnetsByVpc = new HashMap<>();
            subnetworksClient.aggregatedList(projectId).iterateAll().forEach(entry ->
                    entry.getValue().getSubnetworksList().forEach(subnet ->
                            subnetsByVpc.computeIfAbsent(subnet.getNetwork(), k -> new ArrayList<>())
                                    .add(subnet)));

            // ── PARALLEL: Fetch VPCs, Storage, and Disks concurrently ──
            CompletableFuture<List<ResourceNode>> vpcFuture = CompletableFuture.supplyAsync(() -> {
                List<ResourceNode> vpcs = new ArrayList<>();
                networksClient.list(projectId).iterateAll().forEach(network -> {
                    ResourceNode vpcNode = ResourceNode.builder()
                            .id(network.getSelfLink())
                            .name(network.getName())
                            .type("VPC")
                            .details(Map.of("id", String.valueOf(network.getId())))
                            .build();

                    // Distribute pre-fetched subnets and instances — NO extra API calls
                    List<Subnetwork> vpcSubnets = subnetsByVpc.getOrDefault(network.getSelfLink(),
                            Collections.emptyList());
                    List<ResourceNode> subnetNodes = new ArrayList<>();
                    for (Subnetwork subnet : vpcSubnets) {
                        ResourceNode subnetNode = ResourceNode.builder()
                                .id(subnet.getSelfLink())
                                .name(subnet.getName())
                                .type("SUBNET")
                                .details(Map.of("region", subnet.getRegion()))
                                .build();

                        // Distribute pre-fetched instances — NO extra API calls
                        List<Instance> subnetInstances = instancesBySubnet.getOrDefault(
                                subnet.getSelfLink(), Collections.emptyList());
                        List<ResourceNode> instanceNodes = subnetInstances.stream()
                                .map(inst -> ResourceNode.builder()
                                        .id(inst.getSelfLink())
                                        .name(inst.getName())
                                        .type("INSTANCE")
                                        .build())
                                .collect(Collectors.toList());
                        subnetNode.setChildren(instanceNodes);
                        subnetNodes.add(subnetNode);
                    }
                    vpcNode.setChildren(subnetNodes);
                    vpcs.add(vpcNode);
                });
                return vpcs;
            }, executor);

            CompletableFuture<ResourceNode> storageFuture = CompletableFuture.supplyAsync(() -> {
                List<ResourceNode> bucketNodes = new ArrayList<>();
                storage.list().iterateAll().forEach(bucket ->
                        bucketNodes.add(ResourceNode.builder()
                                .id(bucket.getName())
                                .name(bucket.getName())
                                .type("BUCKET")
                                .build()));

                if (bucketNodes.isEmpty()) return null;

                ResourceNode storageSubnet = ResourceNode.builder()
                        .id(projectId + "-gcs-subnet")
                        .name("GCS Buckets")
                        .type("SUBNET")
                        .details(Map.of("synthetic", true))
                        .children(bucketNodes)
                        .build();

                return ResourceNode.builder()
                        .id(projectId + "-gcs-vpc")
                        .name("Cloud Storage")
                        .type("VPC")
                        .details(Map.of("synthetic", true))
                        .children(List.of(storageSubnet))
                        .build();
            }, executor);

            CompletableFuture<ResourceNode> diskFuture = CompletableFuture.supplyAsync(() -> {
                // Group disks by zone
                Map<String, List<Disk>> disksByZone = new LinkedHashMap<>();
                disksClient.aggregatedList(projectId).iterateAll().forEach(entry -> {
                    if (!entry.getValue().getDisksList().isEmpty()) {
                        String zoneName = entry.getKey().replace("zones/", "");
                        disksByZone.put(zoneName, entry.getValue().getDisksList());
                    }
                });

                if (disksByZone.isEmpty()) return null;

                List<ResourceNode> diskSubnets = new ArrayList<>();
                disksByZone.forEach((zoneName, disks) -> {
                    List<ResourceNode> diskNodes = disks.stream()
                            .map(disk -> ResourceNode.builder()
                                    .id(disk.getSelfLink())
                                    .name(disk.getName())
                                    .type("DISK")
                                    .build())
                            .collect(Collectors.toList());

                    diskSubnets.add(ResourceNode.builder()
                            .id(projectId + "-disk-subnet-" + zoneName)
                            .name(zoneName + " Zone")
                            .type("SUBNET")
                            .details(Map.of("synthetic", true, "zone", zoneName))
                            .children(diskNodes)
                            .build());
                });

                return ResourceNode.builder()
                        .id(projectId + "-disks-vpc")
                        .name("Persistent Disks")
                        .type("VPC")
                        .details(Map.of("synthetic", true))
                        .children(diskSubnets)
                        .build();
            }, executor);

            // ── WAIT for all parallel tasks and assemble ──
            List<ResourceNode> children = new ArrayList<>(vpcFuture.join());
            ResourceNode storageNode = storageFuture.join();
            if (storageNode != null) children.add(storageNode);
            ResourceNode diskNode = diskFuture.join();
            if (diskNode != null) children.add(diskNode);

            projectNode.setChildren(children);

            // Compute resource counts from the tree — NO separate API calls
            Map<String, Integer> resourceCounts = computeResourceCountsFromTree(projectNode);
            projectNode.setCount(resourceCounts.values().stream().mapToInt(Integer::intValue).sum());
            projectNode.setResourceCounts(resourceCounts);

        } catch (Exception e) {
            log.warn("Skipping project '{}' — insufficient permissions ({}). "
                    + "This is normal if the service account can discover but not access this project.",
                    projectId, e.getMessage());
        }
        return projectNode;
    }

    /**
     * Derives resource counts by walking the already-built tree.
     * No additional API calls needed.
     */
    private Map<String, Integer> computeResourceCountsFromTree(ResourceNode node) {
        Map<String, Integer> counts = new HashMap<>();
        countByType(node, counts);
        return counts;
    }

    private void countByType(ResourceNode node, Map<String, Integer> counts) {
        if (node == null) return;
        String type = node.getType();
        if (type != null && !"PROJECT".equals(type) && !"PROVIDER".equals(type)) {
            counts.merge(type, 1, Integer::sum);
        }
        if (node.getChildren() != null) {
            for (ResourceNode child : node.getChildren()) {
                countByType(child, counts);
            }
        }
    }

    /**
     * countProjectResources is still used by fetchStats() for lightweight counting.
     */
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
