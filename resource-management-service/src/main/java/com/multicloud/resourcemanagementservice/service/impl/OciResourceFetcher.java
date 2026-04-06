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
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

        ResourceNode rootNode = ResourceNode.builder()
                .id(details.getCompartmentId())
                .name("OCI Compartment")
                .type("COMPARTMENT")
                .details(new HashMap<>(Map.of("tenancyId", details.getTenancyOcid())))
                .build();

        try (VirtualNetworkClient vcnClient = VirtualNetworkClient.builder().build(authProvider);
             ComputeClient computeClient = ComputeClient.builder().build(authProvider);
             ObjectStorageClient storageClient = ObjectStorageClient.builder().build(authProvider)) {

            // 1. Fetch Network Resources (VCNs & Subnets)
            Map<String, ResourceNode> subnetNodes = new HashMap<>();
            Map<String, ResourceNode> vcnNodes = new HashMap<>();
            
            fetchNetworkResources(details.getCompartmentId(), vcnClient, rootNode, vcnNodes, subnetNodes);

            // 2. Fetch Compute Resources (Instances)
            fetchComputeResources(details.getCompartmentId(), computeClient, rootNode, subnetNodes);

            // 3. Fetch Storage Resources (Buckets)
            fetchStorageResources(details.getCompartmentId(), storageClient, rootNode);

            return rootNode;
        } catch (Exception e) {
            log.error("Failed to fetch OCI resources for profile {}: {}", profileId, e.getMessage());
            throw new RuntimeException("OCI Resource Fetching Failed", e);
        }
    }

    private void fetchNetworkResources(String compartmentId, VirtualNetworkClient client, ResourceNode rootNode, 
                                     Map<String, ResourceNode> vcnNodes, Map<String, ResourceNode> subnetNodes) {
        
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
                    log.warn("Failed to fetch subnets for VCN {}: {}", vcn.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to fetch VCNs: {}", e.getMessage());
        }
    }

    private void fetchComputeResources(String compartmentId, ComputeClient client, ResourceNode rootNode, 
                                     Map<String, ResourceNode> subnetNodes) {
        
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
            log.warn("Failed to fetch Instances: {}", e.getMessage());
        }
    }

    private void fetchStorageResources(String compartmentId, ObjectStorageClient client, ResourceNode rootNode) {
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
            log.warn("Failed to fetch Buckets: {}", e.getMessage());
        }
    }
}
