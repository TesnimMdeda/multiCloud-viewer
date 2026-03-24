package com.multicloud.cloudprofileservice.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.repository.GcpProfileDetailsRepository;
import com.multicloud.cloudprofileservice.service.impl.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class GcsStorageAdapter implements CloudStorageAdapter {

    private final GcpProfileDetailsRepository gcpRepo;
    private final EncryptionService            encryptionService;

    @Override
    public CloudProvider getSupportedProvider() { return CloudProvider.GCP; }


    private Storage buildStorageClient(String profileId) {
        var details = gcpRepo.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException(
                        "GCP profile details not found for profile: " + profileId));


        String rawKey = encryptionService.decrypt(details.getEncryptedServiceAccountKey());

        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(rawKey.getBytes()));

            return StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .setProjectId(details.getProjectId())
                    .build()
                    .getService();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build GCS client", e);
        }
    }

    @Override
    public List<String> listBuckets(String profileId) {
        Storage storage = buildStorageClient(profileId);
        return StreamSupport
                .stream(storage.list().iterateAll().spliterator(), false)
                .map(Bucket::getName)
                .toList();
    }

    @Override
    public List<StorageObject> listObjects(String profileId,
                                           String bucketName, String prefix) {
        Storage storage = buildStorageClient(profileId);
        return StreamSupport
                .stream(storage.list(bucketName,
                                Storage.BlobListOption.prefix(prefix != null ? prefix : ""))
                        .iterateAll().spliterator(), false)
                .map(b -> new StorageObject(
                        b.getName(),
                        b.getSize(),
                        b.getContentType(),
                        b.getUpdateTimeOffsetDateTime().toInstant()))
                .toList();
    }

    @Override
    public void uploadObject(String profileId, String bucketName,
                             String objectName, InputStream content, long size) {
        Storage storage = buildStorageClient(profileId);
        BlobId   blobId   = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        try {
            storage.createFrom(blobInfo, content);
        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadObject(String profileId,
                                      String bucketName, String objectName) {
        Storage storage = buildStorageClient(profileId);
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        if (blob == null) throw new RuntimeException("Object not found: " + objectName);
        return new ByteArrayInputStream(blob.getContent());
    }

    @Override
    public void deleteObject(String profileId, String bucketName, String objectName) {
        Storage storage = buildStorageClient(profileId);
        storage.delete(BlobId.of(bucketName, objectName));
    }

    @Override
    public StorageObject getObjectMetadata(String profileId,
                                           String bucketName, String objectName) {
        Storage storage = buildStorageClient(profileId);
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        if (blob == null) throw new RuntimeException("Object not found: " + objectName);
        return new StorageObject(
                blob.getName(),
                blob.getSize(),
                blob.getContentType(),
                blob.getUpdateTimeOffsetDateTime().toInstant());
    }
}