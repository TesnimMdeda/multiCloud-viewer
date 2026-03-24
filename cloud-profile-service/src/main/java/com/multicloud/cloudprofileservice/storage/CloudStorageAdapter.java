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

    /** List all buckets/containers in the cloud account. */
    List<String> listBuckets(String profileId);

    /** List objects in a bucket with optional prefix filter. */
    List<StorageObject> listObjects(String profileId, String bucketName, String prefix);

    /** Upload an object to a bucket. */
    void uploadObject(String profileId, String bucketName,
                      String objectName, InputStream content, long size);

    /** Download an object. Returns InputStream. */
    InputStream downloadObject(String profileId, String bucketName, String objectName);

    /** Delete an object. */
    void deleteObject(String profileId, String bucketName, String objectName);

    /** Get object metadata. */
    StorageObject getObjectMetadata(String profileId, String bucketName, String objectName);

    record StorageObject(
            String  name,
            long    size,
            String  contentType,
            Instant lastModified
    ) {}
}