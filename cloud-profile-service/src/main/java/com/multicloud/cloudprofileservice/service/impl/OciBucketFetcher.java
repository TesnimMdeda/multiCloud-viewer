package com.multicloud.cloudprofileservice.service.impl;

import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.BucketSummary;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import com.multicloud.cloudprofileservice.repository.OciProfileDetailsRepository;
import com.multicloud.cloudprofileservice.service.BucketFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class OciBucketFetcher implements BucketFetcher {

    private final OciProfileDetailsRepository ociRepo;
    private final EncryptionService           encryptionService;

    @Override
    public List<String> fetchBuckets(String profileId) {
        var details = ociRepo.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException("OCI profile not found: " + profileId));

        String privateKey = encryptionService.decrypt(details.getEncryptedPrivateKey());

        try {
            var authProvider = SimpleAuthenticationDetailsProvider.builder()
                    .tenantId(details.getTenancyOcid())
                    .userId(details.getUserOcid())
                    .fingerprint(details.getFingerprint())
                    .region(com.oracle.bmc.Region.fromRegionId(
                            details.getProfile().getRegion()))
                    .privateKeySupplier(() -> new ByteArrayInputStream(
                            privateKey.getBytes(StandardCharsets.UTF_8)))
                    .build();

            ObjectStorageClient client = ObjectStorageClient.builder().build(authProvider);

            String namespace = client.getNamespace(
                            GetNamespaceRequest.builder()
                                    .compartmentId(details.getCompartmentId())
                                    .build())
                    .getValue();

            return client.listBuckets(ListBucketsRequest.builder()
                            .namespaceName(namespace)
                            .compartmentId(details.getCompartmentId())
                            .build())
                    .getItems()
                    .stream()
                    .map(BucketSummary::getName)
                    .toList();

        } catch (Exception e) {
            log.warn("OCI bucket fetch failed for profile {}: {}", profileId, e.getMessage());
            return List.of();
        }
    }
}