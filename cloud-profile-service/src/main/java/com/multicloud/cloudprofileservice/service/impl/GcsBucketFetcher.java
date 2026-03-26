package com.multicloud.cloudprofileservice.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.multicloud.cloudprofileservice.repository.GcpProfileDetailsRepository;
import com.multicloud.cloudprofileservice.service.BucketFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.StreamSupport;

@Slf4j
@Component("gcsBucketFetcher")
@RequiredArgsConstructor
public class GcsBucketFetcher implements BucketFetcher {

    private final GcpProfileDetailsRepository gcpRepo;
    private final EncryptionService           encryptionService;

    @Override
    public List<String> fetchBuckets(String profileId) {
        var details = gcpRepo.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException("GCP profile not found: " + profileId));

        String rawKey = encryptionService.decrypt(details.getEncryptedServiceAccountKey());

        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(rawKey.getBytes()))
                    .createScoped("https://www.googleapis.com/auth/cloud-platform.read-only");

            Storage storage = StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .setProjectId(details.getProjectId())
                    .build()
                    .getService();

            return StreamSupport
                    .stream(storage.list().iterateAll().spliterator(), false)
                    .map(Bucket::getName)
                    .toList();

        } catch (Exception e) {
            log.warn("GCS bucket fetch failed for profile {}: {}", profileId, e.getMessage());
            return List.of();
        }
    }
}