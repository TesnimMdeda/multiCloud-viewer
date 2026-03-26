package com.multicloud.cloudprofileservice.storage;

import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.*;
import com.oracle.bmc.objectstorage.model.BucketSummary;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.entity.OciProfileDetails;
import com.multicloud.cloudprofileservice.repository.OciProfileDetailsRepository;
import com.multicloud.cloudprofileservice.service.impl.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OciStorageAdapter implements CloudStorageAdapter {

    private final OciProfileDetailsRepository ociRepo;
    private final EncryptionService            encryptionService;

    @Override
    public CloudProvider getSupportedProvider() { return CloudProvider.OCI; }


    private ObjectStorageClient buildClient(String profileId) {
        var details = ociRepo.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException(
                        "OCI profile details not found for profile: " + profileId));

        String privateKey = encryptionService.decrypt(details.getEncryptedPrivateKey());

        var authProvider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(details.getTenancyOcid())
                .userId(details.getUserOcid())
                .fingerprint(details.getFingerprint())
                .region(com.oracle.bmc.Region.fromRegionId(
                        details.getProfile().getRegion()))
                .privateKeySupplier(() -> new ByteArrayInputStream(
                        privateKey.getBytes(StandardCharsets.UTF_8)))
                .build();

        return ObjectStorageClient.builder().build(authProvider);
    }

    private String getNamespace(ObjectStorageClient client, OciProfileDetails details) {
        return client.getNamespace(
                        GetNamespaceRequest.builder()
                                .compartmentId(details.getCompartmentId())
                                .build())
                .getValue();
    }

    @Override
    public List<String> listBuckets(String profileId) {
        var client  = buildClient(profileId);
        var details = ociRepo.findByProfileId(profileId).orElseThrow();
        var ns      = getNamespace(client, details);

        return client.listBuckets(ListBucketsRequest.builder()
                        .namespaceName(ns)
                        .compartmentId(details.getCompartmentId())
                        .build())
                .getItems()
                .stream()
                .map(BucketSummary::getName)
                .toList();
    }

    @Override
    public List<StorageObject> listObjects(String profileId,
                                           String bucketName, String prefix) {
        var client  = buildClient(profileId);
        var details = ociRepo.findByProfileId(profileId).orElseThrow();
        var ns      = getNamespace(client, details);

        var req = ListObjectsRequest.builder()
                .namespaceName(ns)
                .bucketName(bucketName)
                .prefix(prefix)
                .build();

        return client.listObjects(req).getListObjects().getObjects().stream()
                .map(o -> new StorageObject(
                        o.getName(),
                        o.getSize() != null ? o.getSize() : 0L,
                        o.getMd5(),   // OCI doesn't expose contentType in listing
                        o.getTimeModified() != null
                                ? o.getTimeModified().toInstant() : null))
                .toList();
    }

    @Override
    public void uploadObject(String profileId, String bucketName,
                             String objectName, InputStream content, long size) {
        var client  = buildClient(profileId);
        var details = ociRepo.findByProfileId(profileId).orElseThrow();
        var ns      = getNamespace(client, details);

        client.putObject(PutObjectRequest.builder()
                .namespaceName(ns)
                .bucketName(bucketName)
                .objectName(objectName)
                .contentLength(size)
                .putObjectBody(content)
                .build());
    }

    @Override
    public InputStream downloadObject(String profileId,
                                      String bucketName, String objectName) {
        var client  = buildClient(profileId);
        var details = ociRepo.findByProfileId(profileId).orElseThrow();
        var ns      = getNamespace(client, details);

        var response = client.getObject(GetObjectRequest.builder()
                .namespaceName(ns)
                .bucketName(bucketName)
                .objectName(objectName)
                .build());

        return response.getInputStream();
    }

    @Override
    public void deleteObject(String profileId, String bucketName, String objectName) {
        var client  = buildClient(profileId);
        var details = ociRepo.findByProfileId(profileId).orElseThrow();
        var ns      = getNamespace(client, details);

        client.deleteObject(DeleteObjectRequest.builder()
                .namespaceName(ns)
                .bucketName(bucketName)
                .objectName(objectName)
                .build());
    }

    @Override
    public StorageObject getObjectMetadata(String profileId,
                                           String bucketName, String objectName) {
        var client  = buildClient(profileId);
        var details = ociRepo.findByProfileId(profileId).orElseThrow();
        var ns      = getNamespace(client, details);

        var response = client.headObject(HeadObjectRequest.builder()
                .namespaceName(ns)
                .bucketName(bucketName)
                .objectName(objectName)
                .build());

        return new StorageObject(
                objectName,
                response.getContentLength() != null ? response.getContentLength() : 0L,
                response.getContentType(),
                response.getLastModified() != null
                        ? response.getLastModified().toInstant() : null);
    }
}