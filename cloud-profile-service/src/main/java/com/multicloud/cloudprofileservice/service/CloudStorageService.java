package com.multicloud.cloudprofileservice.service;

import com.multicloud.cloudprofileservice.storage.CloudStorageAdapter.StorageObject;

import java.io.InputStream;
import java.util.List;

/**
 * Unified storage service — delegates to the correct CloudStorageAdapter
 * based on the profile's provider, and enforces ownership checks.
 */
public interface CloudStorageService {

    List<String> listBuckets(String profileId, String requestingUserId);

    List<StorageObject> listObjects(String profileId, String bucketName,
                                    String prefix, String requestingUserId);

    void uploadObject(String profileId, String bucketName, String objectName,
                      InputStream content, long size, String requestingUserId);

    void deleteObject(String profileId, String bucketName,
                      String objectName, String requestingUserId);

    InputStream downloadObject(String profileId, String bucketName,
                               String objectName, String requestingUserId);

    StorageObject getObjectMetadata(String profileId, String bucketName,
                                    String objectName, String requestingUserId);
}