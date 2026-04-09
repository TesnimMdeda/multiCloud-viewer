package com.multicloud.resourcemanagementservice.service.impl;

import com.multicloud.resourcemanagementservice.client.CloudProfileServiceClient;
import com.multicloud.resourcemanagementservice.client.dto.OciProfileDetailsResponse;
import com.multicloud.resourcemanagementservice.dto.ResourceNode;
import com.multicloud.resourcemanagementservice.service.ResourceFetcher;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.requests.ListInstancesRequest;
import com.oracle.bmc.core.requests.ListSubnetsRequest;
import com.oracle.bmc.core.requests.ListVcnsRequest;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OciResourceFetcher implements ResourceFetcher {

    private final CloudProfileServiceClient profileClient;

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
                .privateKeySupplier(() -> new ByteArrayInputStream(details.getDecryptedPrivateKey().getBytes(StandardCharsets.UTF_8)))
                .region(Region.fromRegionId(details.getRegion()))
                .build();

        List<String> errors = new ArrayList<>();

        ResourceNode rootNode = ResourceNode.builder()
                .id(details.getCompartmentId())
                .name("OCI Compartment")
                .type("COMPARTMENT")
                .details(new HashMap<>(Map.of(
                        "tenancyId", details.getTenancyOcid(),
                        "region", details.getRegion()
                )))
                .build();

        try (VirtualNetworkClient vcnClient = VirtualNetworkClient.builder().build(authProvider);
             ComputeClient computeClient = ComputeClient.builder().build(authProvider);
             ObjectStorageClient storageClient = ObjectStorageClient.builder().build(authProvider)) {

            // 1. Fetch Network Resources (VCNs & Subnets)
            Map<String, ResourceNode> subnetNodes = new HashMap<>();
            Map<String, ResourceNode> vcnNodes = new HashMap<>();
            
            fetchNetworkResources(details.getCompartmentId(), vcnClient, rootNode, vcnNodes, subnetNodes, errors);

            // 2. Fetch Compute Resources (Instances)
            fetchComputeResources(details.getCompartmentId(), computeClient, rootNode, subnetNodes, errors);

            // 3. Fetch Storage Resources (Buckets)
            fetchStorageResources(details.getCompartmentId(), storageClient, rootNode, errors);

            // Add error summary to root node details if any fetch failed
            if (!errors.isEmpty()) {
                Map<String, Object> updatedDetails = new HashMap<>(rootNode.getDetails());
                updatedDetails.put("errors", errors);
                updatedDetails.put("partialResult", true);
                rootNode.setDetails(updatedDetails);
                log.warn("OCI resource fetch for profile {} completed with {} error(s): {}", 
                        profileId, errors.size(), errors);
            }

            return rootNode;
        } catch (Exception e) {
            log.error("Failed to fetch OCI resources for profile {}: {}", profileId, e.getMessage());
            throw new RuntimeException("OCI Resource Fetching Failed: " + buildUserFriendlyMessage(e), e);
        }
    }

    private void fetchNetworkResources(String compartmentId, VirtualNetworkClient client, ResourceNode rootNode, 
                                     Map<String, ResourceNode> vcnNodes, Map<String, ResourceNode> subnetNodes,
                                     List<String> errors) {
        
        try {
            ListVcnsRequest vcnRequest = ListVcnsRequest.builder().compartmentId(compartmentId).build();
            client.listVcns(vcnRequest).getItems().forEach(vcn -> {
                ResourceNode vcnNode = ResourceNode.builder()
                        .id(vcn.getId())
                        .name(vcn.getDisplayName())
                        .type("VCN")
                        .details(Map.of("cidrBlock", vcn.getCidrBlock()))
                        .build();
                vcnNodes.put(vcn.getId(), vcnNode);
                rootNode.addChild(vcnNode);

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
                    String msg = "Failed to fetch subnets for VCN " + vcn.getId() + ": " + buildUserFriendlyMessage(e);
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

    private void fetchComputeResources(String compartmentId, ComputeClient client, ResourceNode rootNode, 
                                     Map<String, ResourceNode> subnetNodes, List<String> errors) {
        
        try {
            ListInstancesRequest request = ListInstancesRequest.builder().compartmentId(compartmentId).build();
            client.listInstances(request).getItems().forEach(instance -> {
                ResourceNode instanceNode = ResourceNode.builder()
                        .id(instance.getId())
                        .name(instance.getDisplayName())
                        .type("INSTANCE")
                        .details(Map.of(
                                "lifecycleState", instance.getLifecycleState().getValue(),
                                "shape", instance.getShape(),
                                "region", instance.getRegion()
                        ))
                        .build();

                rootNode.addChild(instanceNode);
            });
        } catch (Exception e) {
            String msg = "Failed to fetch Instances: " + buildUserFriendlyMessage(e);
            log.warn(msg);
            errors.add(msg);
        }
    }

    private void fetchStorageResources(String compartmentId, ObjectStorageClient client, ResourceNode rootNode,
                                      List<String> errors) {
        try {
            String namespace = client.getNamespace(GetNamespaceRequest.builder().build()).getValue();
            ListBucketsRequest request = ListBucketsRequest.builder()
                    .namespaceName(namespace)
                    .compartmentId(compartmentId)
                    .build();
            
            client.listBuckets(request).getItems().forEach(bucket -> {
                rootNode.addChild(ResourceNode.builder()
                        .id(bucket.getName())
                        .name(bucket.getName())
                        .type("BUCKET")
                        .details(Map.of("namespace", bucket.getNamespace()))
                        .build());
            });
        } catch (Exception e) {
            String msg = "Failed to fetch Buckets: " + buildUserFriendlyMessage(e);
            log.warn(msg);
            errors.add(msg);
        }
    }

    /**
     * Translates raw OCI SDK exceptions into clear, actionable messages.
     */
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
