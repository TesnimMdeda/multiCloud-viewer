package com.multicloud.resourcemanagementservice.service.impl;

import com.multicloud.resourcemanagementservice.client.CloudProfileServiceClient;
import com.multicloud.resourcemanagementservice.client.dto.OciProfileDetailsResponse;
import com.multicloud.resourcemanagementservice.dto.ResourceNode;
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
import com.oracle.bmc.objectstorage.model.BucketSummary;
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
     * Any level without real children receives a placeholder node instead of [] or
     * null.
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

        List<String> errors = new ArrayList<>();

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

            Map<String, ResourceNode> vcnNodes = new HashMap<>();
            Map<String, ResourceNode> subnetNodes = new HashMap<>();

            // ── Step 1: Build the VCN → Subnet backbone ──────────────────────────────
            fetchNetworkResources(details.getCompartmentId(), vcnClient,
                    rootNode, vcnNodes, subnetNodes, errors);

            // ── Step 2: Map Instances to their Subnets via VNIC attachments ──────────
            fetchComputeResources(details.getCompartmentId(), computeClient, vcnClient,
                    rootNode, vcnNodes, subnetNodes, errors);

            // ── Step 3: Buckets under a synthetic "Object Storage" VCN ───────────────
            fetchStorageResources(details.getCompartmentId(), storageClient, rootNode, errors);

            // ── Step 4: Persist any partial-fetch errors in root details ──────────────
            if (!errors.isEmpty()) {
                Map<String, Object> updatedDetails = new HashMap<>(rootNode.getDetails());
                updatedDetails.put("errors", errors);
                updatedDetails.put("partialResult", true);
                rootNode.setDetails(updatedDetails);
                log.warn("OCI fetch for profile {} completed with {} error(s): {}",
                        profileId, errors.size(), errors);
            }

            // ── Step 5: Guarantee COMPARTMENT → VCN → SUBNET → RESOURCE depth ────────
            rootNode.ensureHierarchy(HIERARCHY);

            // ── Step 6: Populate count field on every node (real children only) ───────
            rootNode.computeCounts();

            return rootNode;

        } catch (Exception e) {
            log.error("Failed to fetch OCI resources for profile {}: {}", profileId, e.getMessage());
            throw new RuntimeException("OCI Resource Fetching Failed: " + buildUserFriendlyMessage(e), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Network: VCNs and their Subnets
    // ─────────────────────────────────────────────────────────────────────────────

    private void fetchNetworkResources(String compartmentId,
            VirtualNetworkClient client,
            ResourceNode rootNode,
            Map<String, ResourceNode> vcnNodes,
            Map<String, ResourceNode> subnetNodes,
            List<String> errors) {
        try {
            ListVcnsRequest vcnRequest = ListVcnsRequest.builder()
                    .compartmentId(compartmentId).build();

            client.listVcns(vcnRequest).getItems().forEach(vcn -> {
                ResourceNode vcnNode = ResourceNode.builder()
                        .id(vcn.getId())
                        .name(vcn.getDisplayName())
                        .type("VCN")
                        .details(Map.of("cidrBlock", vcn.getCidrBlock()))
                        .build();
                vcnNodes.put(vcn.getId(), vcnNode);
                rootNode.addChild(vcnNode);

                // Fetch subnets for this VCN
                try {
                    ListSubnetsRequest subnetRequest = ListSubnetsRequest.builder()
                            .compartmentId(compartmentId)
                            .vcnId(vcn.getId())
                            .build();
                    client.listSubnets(subnetRequest).getItems().forEach(subnet -> {
                        ResourceNode subnetNode = ResourceNode.builder()
                                .id(subnet.getId())
                                .name(subnet.getDisplayName())
                                .type("SUBNET")
                                .details(Map.of("cidrBlock", subnet.getCidrBlock()))
                                .build();
                        subnetNodes.put(subnet.getId(), subnetNode);
                        vcnNode.addChild(subnetNode);
                    });
                } catch (Exception e) {
                    String msg = "Failed to fetch subnets for VCN " + vcn.getId()
                            + ": " + buildUserFriendlyMessage(e);
                    log.warn(msg);
                    errors.add(msg);
                }
            });
        } catch (Exception e) {
            String msg = "Failed to fetch VCNs: " + buildUserFriendlyMessage(e);
            log.warn(msg);
            errors.add(msg);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Compute: Instances mapped to Subnets via VNIC attachments
    // ─────────────────────────────────────────────────────────────────────────────

    private void fetchComputeResources(String compartmentId,
            ComputeClient computeClient,
            VirtualNetworkClient vcnClient,
            ResourceNode rootNode,
            Map<String, ResourceNode> vcnNodes,
            Map<String, ResourceNode> subnetNodes,
            List<String> errors) {
        try {
            // Build instanceId → subnetId map from VNIC attachments
            Map<String, String> instanceToSubnet = buildInstanceToSubnetMap(compartmentId, computeClient, vcnClient,
                    errors);

            ListInstancesRequest request = ListInstancesRequest.builder()
                    .compartmentId(compartmentId).build();

            computeClient.listInstances(request).getItems().forEach(instance -> {
                Map<String, Object> details = new HashMap<>();
                details.put("lifecycleState", instance.getLifecycleState().getValue());
                details.put("shape", instance.getShape());
                details.put("region", instance.getRegion());

                ResourceNode instanceNode = ResourceNode.builder()
                        .id(instance.getId())
                        .name(instance.getDisplayName())
                        .type("INSTANCE")
                        .details(details)
                        .build();

                String subnetId = instanceToSubnet.get(instance.getId());
                ResourceNode targetSubnet = subnetId != null ? subnetNodes.get(subnetId) : null;

                if (targetSubnet != null) {
                    // Happy path: instance sits inside a known subnet
                    targetSubnet.addChild(instanceNode);
                } else {
                    // Fallback: place under a synthetic subnet to preserve hierarchy depth
                    ResourceNode fallback = getOrCreateFallbackSubnet(rootNode, vcnNodes);
                    fallback.addChild(instanceNode);
                }
            });
        } catch (Exception e) {
            String msg = "Failed to fetch Instances: " + buildUserFriendlyMessage(e);
            log.warn(msg);
            errors.add(msg);
        }
    }

    /**
     * Uses VNIC attachments to resolve each Instance OCID → Subnet OCID.
     * GetVnic is called per unique VNIC; failures are tolerated and logged.
     */
    private Map<String, String> buildInstanceToSubnetMap(String compartmentId,
            ComputeClient computeClient,
            VirtualNetworkClient vcnClient,
            List<String> errors) {
        Map<String, String> instanceToSubnet = new HashMap<>();
        try {
            ListVnicAttachmentsRequest attachRequest = ListVnicAttachmentsRequest.builder()
                    .compartmentId(compartmentId).build();

            computeClient.listVnicAttachments(attachRequest).getItems().forEach(attachment -> {
                if (attachment.getLifecycleState() == VnicAttachment.LifecycleState.Attached) {
                    try {
                        GetVnicRequest getVnicRequest = GetVnicRequest.builder()
                                .vnicId(attachment.getVnicId()).build();
                        String subnetId = vcnClient.getVnic(getVnicRequest).getVnic().getSubnetId();
                        instanceToSubnet.put(attachment.getInstanceId(), subnetId);
                    } catch (Exception e) {
                        log.debug("Could not resolve VNIC {} to subnet: {}",
                                attachment.getVnicId(), e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            String msg = "Could not fetch VNIC attachments for subnet mapping: " + e.getMessage();
            log.warn(msg);
            errors.add(msg);
        }
        return instanceToSubnet;
    }

    /**
     * Returns a synthetic SUBNET node for instances that could not be mapped to a
     * real subnet.
     * The synthetic node is attached to the first available real VCN, or to a new
     * synthetic VCN
     * if none exist. This ensures the COMPARTMENT → VCN → SUBNET depth is always
     * respected.
     */
    private ResourceNode getOrCreateFallbackSubnet(ResourceNode rootNode,
            Map<String, ResourceNode> vcnNodes) {
        final String FALLBACK_SUBNET_ID = "oci-unattached-subnet";

        // Prefer the first real VCN if one exists
        if (!vcnNodes.isEmpty()) {
            ResourceNode firstVcn = vcnNodes.values().iterator().next();
            return firstVcn.getChildren().stream()
                    .filter(c -> FALLBACK_SUBNET_ID.equals(c.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        ResourceNode sub = ResourceNode.builder()
                                .id(FALLBACK_SUBNET_ID)
                                .name("Unattached Compute")
                                .type("SUBNET")
                                .details(Map.of("synthetic", true))
                                .build();
                        firstVcn.addChild(sub);
                        return sub;
                    });
        }

        // No real VCNs — create a fully synthetic VCN + Subnet
        final String FALLBACK_VCN_ID = "oci-compute-vcn";
        ResourceNode synVcn = rootNode.getChildren().stream()
                .filter(c -> FALLBACK_VCN_ID.equals(c.getId()))
                .findFirst()
                .orElseGet(() -> {
                    ResourceNode vcn = ResourceNode.builder()
                            .id(FALLBACK_VCN_ID)
                            .name("Compute Network")
                            .type("VCN")
                            .details(Map.of("synthetic", true))
                            .build();
                    rootNode.addChild(vcn);
                    return vcn;
                });

        return synVcn.getChildren().stream()
                .filter(c -> FALLBACK_SUBNET_ID.equals(c.getId()))
                .findFirst()
                .orElseGet(() -> {
                    ResourceNode sub = ResourceNode.builder()
                            .id(FALLBACK_SUBNET_ID)
                            .name("Compute Subnet")
                            .type("SUBNET")
                            .details(Map.of("synthetic", true))
                            .build();
                    synVcn.addChild(sub);
                    return sub;
                });
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Storage: Buckets grouped under a synthetic "Object Storage" VCN
    // ─────────────────────────────────────────────────────────────────────────────

    private void fetchStorageResources(String compartmentId,
            ObjectStorageClient client,
            ResourceNode rootNode,
            List<String> errors) {
        try {
            String namespace = client.getNamespace(GetNamespaceRequest.builder().build()).getValue();
            ListBucketsRequest request = ListBucketsRequest.builder()
                    .namespaceName(namespace)
                    .compartmentId(compartmentId)
                    .build();

            List<BucketSummary> buckets = client.listBuckets(request).getItems();
            if (buckets.isEmpty())
                return;

            // Synthetic VCN — represents the Object Storage service
            ResourceNode storageVcn = ResourceNode.builder()
                    .id("oci-object-storage-vcn")
                    .name("Object Storage")
                    .type("VCN")
                    .details(Map.of("synthetic", true, "namespace", namespace))
                    .build();

            // Synthetic Subnet — groups all buckets at the same depth as real subnets
            ResourceNode bucketSubnet = ResourceNode.builder()
                    .id("oci-buckets-subnet")
                    .name("Buckets")
                    .type("SUBNET")
                    .details(Map.of("synthetic", true))
                    .build();

            storageVcn.addChild(bucketSubnet);
            rootNode.addChild(storageVcn);

            buckets.forEach(bucket -> bucketSubnet.addChild(ResourceNode.builder()
                    .id(bucket.getName())
                    .name(bucket.getName())
                    .type("BUCKET")
                    .details(Map.of("namespace", bucket.getNamespace()))
                    .build()));

        } catch (Exception e) {
            String msg = "Failed to fetch Buckets: " + buildUserFriendlyMessage(e);
            log.warn(msg);
            errors.add(msg);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Error translation
    // ─────────────────────────────────────────────────────────────────────────────

    private String buildUserFriendlyMessage(Exception e) {
        if (e instanceof BmcException bmcEx) {
            int status = bmcEx.getStatusCode();
            String serviceCode = bmcEx.getServiceCode();

            if (status == 404 && "NotAuthorizedOrNotFound".equals(serviceCode)) {
                return "Authorization failed or resource not found. "
                        + "Please verify: (1) the Compartment OCID exists and belongs to your tenancy, "
                        + "(2) your API key user has the required IAM policies "
                        + "(e.g. 'Allow group <group> to read all-resources in compartment <name>'), "
                        + "(3) the region is correct.";
            }
            if (status == 404 && "NamespaceNotFound".equals(serviceCode)) {
                return "Object Storage namespace not found. "
                        + "Please verify: (1) the Compartment OCID is correct, "
                        + "(2) your API key user has IAM policies for Object Storage "
                        + "(e.g. 'Allow group <group> to read object-family in compartment <name>').";
            }
            if (status == 401) {
                return "Authentication failed. "
                        + "Please verify your API key credentials: tenancy OCID, user OCID, fingerprint, and private key.";
            }
            if (status == 403) {
                return "Access denied. Your API key user does not have permission for this operation. "
                        + "Add the required IAM policy in the OCI Console under Identity → Policies.";
            }
            return String.format("OCI API error (HTTP %d, %s): %s", status, serviceCode, bmcEx.getMessage());
        }
        return e.getMessage();
    }
}
