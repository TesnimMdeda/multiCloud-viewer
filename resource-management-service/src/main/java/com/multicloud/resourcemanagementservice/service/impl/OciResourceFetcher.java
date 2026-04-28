package com.multicloud.resourcemanagementservice.service.impl;

import com.multicloud.resourcemanagementservice.client.CloudProfileServiceClient;
import com.multicloud.resourcemanagementservice.client.dto.OciProfileDetailsResponse;
import com.multicloud.resourcemanagementservice.dto.ResourceNode;
import com.multicloud.resourcemanagementservice.dto.ResourceStats;
import com.multicloud.resourcemanagementservice.service.ResourceFetcher;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.VnicAttachment;
import com.oracle.bmc.core.requests.GetVnicRequest;
import com.oracle.bmc.core.requests.ListInstancesRequest;
import com.oracle.bmc.core.requests.ListSubnetsRequest;
import com.oracle.bmc.core.requests.ListVcnsRequest;
import com.oracle.bmc.core.requests.ListVnicAttachmentsRequest;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OciResourceFetcher implements ResourceFetcher {

    private final CloudProfileServiceClient profileClient;

    /**
     * Fixed-depth hierarchy enforced for every OCI tree:
     * COMPARTMENT → VCN → SUBNET → RESOURCE (leaf)
     */
    private static final Map<String, String> HIERARCHY = Map.of(
            "COMPARTMENT", "VCN",
            "VCN", "SUBNET",
            "SUBNET", "RESOURCE");

    @Override
    public boolean supports(String provider) {
        return "OCI".equalsIgnoreCase(provider);
    }

    @Override
    public ResourceStats fetchStats(String profileId) {
        OciProfileDetailsResponse details = profileClient.getOciProfileDetails(profileId).getData();

        SimpleAuthenticationDetailsProvider authProvider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(details.getTenancyOcid())
                .userId(details.getUserOcid())
                .fingerprint(details.getFingerprint())
                .privateKeySupplier(() -> new ByteArrayInputStream(
                        details.getDecryptedPrivateKey().getBytes(StandardCharsets.UTF_8)))
                .region(Region.fromRegionId(details.getRegion()))
                .build();

        try (VirtualNetworkClient vcnClient = VirtualNetworkClient.builder().build(authProvider);
                ComputeClient computeClient = ComputeClient.builder().build(authProvider);
                ObjectStorageClient storageClient = ObjectStorageClient.builder().build(authProvider)) {

            Map<String, Integer> counts = countCompartmentResources(details.getCompartmentId(), vcnClient,
                    computeClient, storageClient);

            return ResourceStats.builder()
                    .provider("OCI")
                    .projectCount(1)
                    .vpcCount(counts.getOrDefault("VCN", 0))
                    .subnetCount(counts.getOrDefault("SUBNET", 0))
                    .computeCount(counts.getOrDefault("INSTANCE", 0))
                    .storageCount(counts.getOrDefault("BUCKET", 0))
                    .build();

        } catch (Exception e) {
            log.error("Failed to fetch OCI stats: {}", e.getMessage());
            throw new RuntimeException("OCI Stats Fetch Failed", e);
        }
    }

    @Override
    public ResourceNode fetchResources(String profileId) {
        OciProfileDetailsResponse details = profileClient.getOciProfileDetails(profileId).getData();

        SimpleAuthenticationDetailsProvider authProvider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(details.getTenancyOcid())
                .userId(details.getUserOcid())
                .fingerprint(details.getFingerprint())
                .privateKeySupplier(() -> new ByteArrayInputStream(
                        details.getDecryptedPrivateKey().getBytes(StandardCharsets.UTF_8)))
                .region(Region.fromRegionId(details.getRegion()))
                .build();

        ResourceNode rootNode = ResourceNode.builder()
                .id(details.getCompartmentId())
                .name("OCI Compartment")
                .type("COMPARTMENT")
                .details(new HashMap<>(Map.of(
                        "tenancyId", details.getTenancyOcid(),
                        "region", details.getRegion())))
                .build();

        try (VirtualNetworkClient vcnClient = VirtualNetworkClient.builder().build(authProvider);
                ComputeClient computeClient = ComputeClient.builder().build(authProvider);
                ObjectStorageClient storageClient = ObjectStorageClient.builder().build(authProvider)) {

            String compartmentId = details.getCompartmentId();

            // ── PRE-FETCH: Get namespace ONCE (was called 3 times before) ──
            String namespace = storageClient.getNamespace(GetNamespaceRequest.builder().build()).getValue();

            // ── PRE-FETCH: Build instance-to-subnet map ONCE (was rebuilt per subnet before) ──
            Map<String, String> instanceToSubnet = buildInstanceToSubnetMapParallel(
                    compartmentId, computeClient, vcnClient);

            // ── PRE-FETCH: List all instances ONCE ──
            var allInstances = computeClient.listInstances(
                    ListInstancesRequest.builder().compartmentId(compartmentId).build()).getItems();

            // Build instance nodes grouped by subnet
            Map<String, List<ResourceNode>> instanceNodesBySubnet = new HashMap<>();
            allInstances.forEach(instance -> {
                String subnetId = instanceToSubnet.get(instance.getId());
                if (subnetId != null) {
                    instanceNodesBySubnet.computeIfAbsent(subnetId, k -> new ArrayList<>())
                            .add(ResourceNode.builder()
                                    .id(instance.getId())
                                    .name(instance.getDisplayName())
                                    .type("INSTANCE")
                                    .details(Map.of("shape", instance.getShape(), "state",
                                            instance.getLifecycleState().getValue()))
                                    .build());
                }
            });

            // ── BUILD TREE: VCNs + Subnets using pre-fetched data — NO extra API calls ──
            List<ResourceNode> children = new ArrayList<>();

            vcnClient.listVcns(ListVcnsRequest.builder().compartmentId(compartmentId).build()).getItems()
                    .forEach(vcn -> {
                        ResourceNode vcnNode = ResourceNode.builder()
                                .id(vcn.getId())
                                .name(vcn.getDisplayName())
                                .type("VCN")
                                .details(Map.of("cidrBlock", vcn.getCidrBlock()))
                                .build();

                        // List subnets for this VCN
                        List<ResourceNode> subnetNodes = new ArrayList<>();
                        vcnClient.listSubnets(ListSubnetsRequest.builder()
                                        .compartmentId(compartmentId).vcnId(vcn.getId()).build())
                                .getItems().forEach(subnet -> {
                                    ResourceNode subnetNode = ResourceNode.builder()
                                            .id(subnet.getId())
                                            .name(subnet.getDisplayName())
                                            .type("SUBNET")
                                            .details(Map.of("cidrBlock", subnet.getCidrBlock()))
                                            .build();

                                    // Distribute pre-fetched instances — NO extra API calls
                                    subnetNode.setChildren(instanceNodesBySubnet.getOrDefault(
                                            subnet.getId(), Collections.emptyList()));
                                    subnetNodes.add(subnetNode);
                                });

                        vcnNode.setChildren(subnetNodes);
                        children.add(vcnNode);
                    });

            // ── Object Storage synthetic VCN ──
            var buckets = storageClient.listBuckets(
                    ListBucketsRequest.builder().namespaceName(namespace)
                            .compartmentId(compartmentId).build()).getItems();

            if (!buckets.isEmpty()) {
                List<ResourceNode> bucketNodes = buckets.stream()
                        .map(bucket -> ResourceNode.builder()
                                .id(bucket.getName())
                                .name(bucket.getName())
                                .type("BUCKET")
                                .details(Map.of("namespace", bucket.getNamespace()))
                                .build())
                        .collect(Collectors.toList());

                ResourceNode bucketSubnet = ResourceNode.builder()
                        .id("oci-buckets-subnet")
                        .name("Buckets")
                        .type("SUBNET")
                        .details(Map.of("synthetic", true))
                        .children(bucketNodes)
                        .build();

                ResourceNode storageVcn = ResourceNode.builder()
                        .id("oci-object-storage-vcn")
                        .name("Object Storage")
                        .type("VCN")
                        .details(Map.of("synthetic", true, "namespace", namespace))
                        .children(List.of(bucketSubnet))
                        .build();

                children.add(storageVcn);
            }

            rootNode.setChildren(children);

            // Compute resource counts from the tree — NO separate countCompartmentResources() call
            Map<String, Integer> resourceCounts = computeResourceCountsFromTree(rootNode);
            int totalCount = resourceCounts.values().stream().mapToInt(Integer::intValue).sum();
            rootNode.setCount(totalCount);
            rootNode.setResourceCounts(resourceCounts);

            // Enforce hierarchy skeleton and compute nested counts
            rootNode.ensureHierarchy(HIERARCHY);
            rootNode.computeCounts();

            return rootNode;

        } catch (Exception e) {
            log.error("Failed to perform deep OCI discovery for profile {}: {}", profileId, e.getMessage());
            throw new RuntimeException("OCI Resource Discovery Failed: " + buildUserFriendlyMessage(e), e);
        }
    }

    /**
     * Builds the instance-to-subnet map with parallel VNIC resolution.
     * Each getVnic() call is dispatched to a thread pool instead of running sequentially.
     */
    private Map<String, String> buildInstanceToSubnetMapParallel(String compartmentId,
            ComputeClient computeClient, VirtualNetworkClient vcnClient) {
        Map<String, String> instanceToSubnet = new ConcurrentHashMap<>();

        try {
            var attachments = computeClient.listVnicAttachments(
                    ListVnicAttachmentsRequest.builder().compartmentId(compartmentId).build()).getItems();

            List<VnicAttachment> activeAttachments = attachments.stream()
                    .filter(a -> a.getLifecycleState() == VnicAttachment.LifecycleState.Attached)
                    .collect(Collectors.toList());

            // Resolve VNICs in parallel — was sequential N calls before
            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(activeAttachments.size(), 10));
            try {
                List<CompletableFuture<Void>> futures = activeAttachments.stream()
                        .map(attachment -> CompletableFuture.runAsync(() -> {
                            try {
                                String subnetId = vcnClient
                                        .getVnic(GetVnicRequest.builder().vnicId(attachment.getVnicId()).build())
                                        .getVnic().getSubnetId();
                                instanceToSubnet.put(attachment.getInstanceId(), subnetId);
                            } catch (Exception e) {
                                log.debug("Could not resolve VNIC {} to subnet: {}", attachment.getVnicId(),
                                        e.getMessage());
                            }
                        }, executor))
                        .collect(Collectors.toList());

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                executor.shutdown();
            }
        } catch (Exception e) {
            log.warn("Could not fetch VNIC attachments: {}", e.getMessage());
        }
        return instanceToSubnet;
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
        if (type != null && !"COMPARTMENT".equals(type)) {
            counts.merge(type, 1, Integer::sum);
        }
        if (node.getChildren() != null) {
            for (ResourceNode child : node.getChildren()) {
                countByType(child, counts);
            }
        }
    }

    /**
     * countCompartmentResources is still used by fetchStats() for lightweight counting.
     */
    private Map<String, Integer> countCompartmentResources(String compartmentId,
            VirtualNetworkClient vcnClient,
            ComputeClient computeClient,
            ObjectStorageClient storageClient) {
        Map<String, Integer> counts = new HashMap<>();
        try {
            int vcnCount = vcnClient.listVcns(ListVcnsRequest.builder().compartmentId(compartmentId).build()).getItems()
                    .size();
            counts.put("VCN", vcnCount);

            int instanceCount = computeClient
                    .listInstances(ListInstancesRequest.builder().compartmentId(compartmentId).build()).getItems()
                    .size();
            counts.put("INSTANCE", instanceCount);

            int subnetCount = vcnClient.listSubnets(ListSubnetsRequest.builder().compartmentId(compartmentId).build())
                    .getItems().size();
            counts.put("SUBNET", subnetCount);

            String namespace = storageClient.getNamespace(GetNamespaceRequest.builder().build()).getValue();
            int bucketCount = storageClient
                    .listBuckets(
                            ListBucketsRequest.builder().namespaceName(namespace).compartmentId(compartmentId).build())
                    .getItems().size();
            if (bucketCount > 0) {
                counts.put("BUCKET", bucketCount);
                counts.put("VCN", counts.getOrDefault("VCN", 0) + 1);
            }
        } catch (Exception e) {
            log.warn("Error counting OCI resources: {}", e.getMessage());
        }
        return counts;
    }

    private String buildUserFriendlyMessage(Exception e) {
        if (e instanceof BmcException bmcEx) {
            int status = bmcEx.getStatusCode();
            String serviceCode = bmcEx.getServiceCode();
            if (status == 404 && "NotAuthorizedOrNotFound".equals(serviceCode)) {
                return "Authorization failed or resource not found. Verify Compartment OCID and IAM policies.";
            }
            return String.format("OCI API error (HTTP %d, %s): %s", status, serviceCode, bmcEx.getMessage());
        }
        return e.getMessage();
    }
}
