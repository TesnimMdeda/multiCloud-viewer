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

            // Perform deep recursive discovery
            List<ResourceNode> children = fetchDeepCompartmentChildren(details.getCompartmentId(), 
                    vcnClient, computeClient, storageClient);
            rootNode.setChildren(children);

            // Fetch aggregate counts for top-level stats
            Map<String, Integer> counts = countCompartmentResources(details.getCompartmentId(), vcnClient,
                    computeClient, storageClient);
            int totalCount = counts.values().stream().mapToInt(Integer::intValue).sum();

            rootNode.setCount(totalCount);
            rootNode.setResourceCounts(counts);

            // Enforce hierarchy skeleton and compute nested counts
            rootNode.ensureHierarchy(HIERARCHY);
            rootNode.computeCounts();

            return rootNode;

        } catch (Exception e) {
            log.error("Failed to perform deep OCI discovery for profile {}: {}", profileId, e.getMessage());
            throw new RuntimeException("OCI Resource Discovery Failed: " + buildUserFriendlyMessage(e), e);
        }
    }

    private List<ResourceNode> fetchDeepCompartmentChildren(String compartmentId,
            VirtualNetworkClient vcnClient,
            ComputeClient computeClient,
            ObjectStorageClient storageClient) {
        List<ResourceNode> children = new ArrayList<>();
        try {
            vcnClient.listVcns(ListVcnsRequest.builder().compartmentId(compartmentId).build()).getItems()
                    .forEach(vcn -> {
                        ResourceNode vcnNode = ResourceNode.builder()
                                .id(vcn.getId())
                                .name(vcn.getDisplayName())
                                .type("VCN")
                                .details(Map.of("cidrBlock", vcn.getCidrBlock()))
                                .build();
                        vcnNode.setChildren(fetchDeepVcnChildren(vcn.getId(), compartmentId, vcnClient, computeClient,
                                storageClient));
                        children.add(vcnNode);
                    });

            String namespace = storageClient.getNamespace(GetNamespaceRequest.builder().build()).getValue();
            if (storageClient
                    .listBuckets(
                            ListBucketsRequest.builder().namespaceName(namespace).compartmentId(compartmentId).build())
                    .getItems().size() > 0) {
                ResourceNode storageVcn = ResourceNode.builder()
                        .id("oci-object-storage-vcn")
                        .name("Object Storage")
                        .type("VCN")
                        .details(Map.of("synthetic", true, "namespace", namespace))
                        .build();
                storageVcn.setChildren(fetchDeepVcnChildren(storageVcn.getId(), compartmentId, vcnClient, computeClient,
                        storageClient));
                children.add(storageVcn);
            }
        } catch (Exception e) {
            log.error("Error building deep OCI compartment tree: {}", e.getMessage());
        }
        return children;
    }

    private List<ResourceNode> fetchDeepVcnChildren(String vcnId, String compartmentId,
            VirtualNetworkClient vcnClient, ComputeClient computeClient, ObjectStorageClient storageClient) {
        List<ResourceNode> subnets = new ArrayList<>();
        try {
            if ("oci-object-storage-vcn".equals(vcnId)) {
                ResourceNode bucketSubnet = ResourceNode.builder()
                        .id("oci-buckets-subnet")
                        .name("Buckets")
                        .type("SUBNET")
                        .details(Map.of("synthetic", true))
                        .build();
                bucketSubnet.setChildren(fetchSubnetChildren(bucketSubnet.getId(), compartmentId, computeClient,
                        vcnClient, storageClient));
                subnets.add(bucketSubnet);
            } else {
                vcnClient.listSubnets(ListSubnetsRequest.builder().compartmentId(compartmentId).vcnId(vcnId).build())
                        .getItems().forEach(subnet -> {
                            ResourceNode subnetNode = ResourceNode.builder()
                                    .id(subnet.getId())
                                    .name(subnet.getDisplayName())
                                    .type("SUBNET")
                                    .details(Map.of("cidrBlock", subnet.getCidrBlock()))
                                    .build();
                            subnetNode.setChildren(fetchSubnetChildren(subnet.getId(), compartmentId, computeClient,
                                    vcnClient, storageClient));
                            subnets.add(subnetNode);
                        });
            }
        } catch (Exception e) {
            log.error("Error building deep OCI VCN tree: {}", e.getMessage());
        }
        return subnets;
    }

    private List<ResourceNode> fetchSubnetChildren(String subnetId, String compartmentId,
            ComputeClient computeClient, VirtualNetworkClient vcnClient, ObjectStorageClient storageClient) {
        List<ResourceNode> children = new ArrayList<>();
        try {
            if ("oci-buckets-subnet".equals(subnetId)) {
                String namespace = storageClient.getNamespace(GetNamespaceRequest.builder().build()).getValue();
                storageClient.listBuckets(
                        ListBucketsRequest.builder().namespaceName(namespace).compartmentId(compartmentId).build())
                        .getItems().forEach(bucket -> {
                            children.add(ResourceNode.builder()
                                    .id(bucket.getName())
                                    .name(bucket.getName())
                                    .type("BUCKET")
                                    .details(Map.of("namespace", bucket.getNamespace()))
                                    .build());
                        });
                return children;
            }

            Map<String, String> instanceToSubnet = buildInstanceToSubnetMap(compartmentId, computeClient, vcnClient);
            computeClient.listInstances(ListInstancesRequest.builder().compartmentId(compartmentId).build()).getItems()
                    .forEach(instance -> {
                        if (subnetId.equals(instanceToSubnet.get(instance.getId()))) {
                            children.add(ResourceNode.builder()
                                    .id(instance.getId())
                                    .name(instance.getDisplayName())
                                    .type("INSTANCE")
                                    .details(Map.of("shape", instance.getShape(), "state",
                                            instance.getLifecycleState().getValue()))
                                    .build());
                        }
                    });
        } catch (Exception e) {
            log.error("Error fetching subnet children: {}", e.getMessage());
        }
        return children;
    }

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

    private Map<String, String> buildInstanceToSubnetMap(String compartmentId,
            ComputeClient computeClient,
            VirtualNetworkClient vcnClient) {
        Map<String, String> instanceToSubnet = new HashMap<>();
        try {
            computeClient.listVnicAttachments(ListVnicAttachmentsRequest.builder().compartmentId(compartmentId).build())
                    .getItems().forEach(attachment -> {
                        if (attachment.getLifecycleState() == VnicAttachment.LifecycleState.Attached) {
                            try {
                                String subnetId = vcnClient
                                        .getVnic(GetVnicRequest.builder().vnicId(attachment.getVnicId()).build())
                                        .getVnic().getSubnetId();
                                instanceToSubnet.put(attachment.getInstanceId(), subnetId);
                            } catch (Exception e) {
                                log.debug("Could not resolve VNIC {} to subnet: {}", attachment.getVnicId(),
                                        e.getMessage());
                            }
                        }
                    });
        } catch (Exception e) {
            log.warn("Could not fetch VNIC attachments: {}", e.getMessage());
        }
        return instanceToSubnet;
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
