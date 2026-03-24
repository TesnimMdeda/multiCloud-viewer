package com.multicloud.cloudprofileservice.storage;

import com.multicloud.cloudprofileservice.entity.CloudProvider;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * Unified cloud storage interface.
 * Implement this to support a new cloud provider (AWS S3, Azure Blob...).
 * The StorageAdapterFactory auto-discovers new @Component implementations.
 */
public interface CloudStorageAdapter {

    CloudProvider getSupportedProvider();

    List<String> listBuckets(String profileId);

    List<StorageObject> listObjects(String profileId, String bucketName, String prefix);

    void uploadObject(String profileId, String bucketName,
                      String objectName, InputStream content, long size);

    InputStream downloadObject(String profileId, String bucketName, String objectName);

    void deleteObject(String profileId, String bucketName, String objectName);

    StorageObject getObjectMetadata(String profileId, String bucketName, String objectName);

    record StorageObject(
            String  name,
            long    size,
            String  contentType,
            Instant lastModified
    ) {}
}