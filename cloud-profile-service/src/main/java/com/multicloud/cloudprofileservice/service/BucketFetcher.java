package com.multicloud.cloudprofileservice.service;

import java.util.List;

/**
 * Minimal interface — just fetch bucket names to confirm connectivity.
 * No upload, download, delete, or object listing.
 */
public interface BucketFetcher {
    List<String> fetchBuckets(String profileId);
}