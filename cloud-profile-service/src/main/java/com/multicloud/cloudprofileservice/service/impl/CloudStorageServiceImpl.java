package com.multicloud.cloudprofileservice.service.impl;

import com.multicloud.cloudprofileservice.entity.CloudProfile;
import com.multicloud.cloudprofileservice.exception.ProfileNotFoundException;
import com.multicloud.cloudprofileservice.exception.UnauthorizedActionException;
import com.multicloud.cloudprofileservice.repository.CloudProfileRepository;
import com.multicloud.cloudprofileservice.service.CloudStorageService;
import com.multicloud.cloudprofileservice.storage.CloudStorageAdapter;
import com.multicloud.cloudprofileservice.storage.CloudStorageAdapter.StorageObject;
import com.multicloud.cloudprofileservice.storage.StorageAdapterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CloudStorageServiceImpl implements CloudStorageService {

    private final CloudProfileRepository profileRepository;
    private final StorageAdapterFactory  adapterFactory;


    private CloudStorageAdapter resolveAdapter(String profileId, String requestingUserId) {
        CloudProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId));

        if (!profile.getOwnerId().equals(requestingUserId)) {
            throw new UnauthorizedActionException("You do not own this profile");
        }

        return adapterFactory.getAdapter(profile.getProvider());
    }


    @Override
    public List<String> listBuckets(String profileId, String requestingUserId) {
        return resolveAdapter(profileId, requestingUserId).listBuckets(profileId);
    }

    @Override
    public List<StorageObject> listObjects(String profileId, String bucketName,
                                           String prefix, String requestingUserId) {
        return resolveAdapter(profileId, requestingUserId)
                .listObjects(profileId, bucketName, prefix);
    }

    @Override
    @Transactional
    public void uploadObject(String profileId, String bucketName, String objectName,
                             InputStream content, long size, String requestingUserId) {
        resolveAdapter(profileId, requestingUserId)
                .uploadObject(profileId, bucketName, objectName, content, size);
    }

    @Override
    @Transactional
    public void deleteObject(String profileId, String bucketName,
                             String objectName, String requestingUserId) {
        resolveAdapter(profileId, requestingUserId)
                .deleteObject(profileId, bucketName, objectName);
    }

    @Override
    public InputStream downloadObject(String profileId, String bucketName,
                                      String objectName, String requestingUserId) {
        return resolveAdapter(profileId, requestingUserId)
                .downloadObject(profileId, bucketName, objectName);
    }

    @Override
    public StorageObject getObjectMetadata(String profileId, String bucketName,
                                           String objectName, String requestingUserId) {
        return resolveAdapter(profileId, requestingUserId)
                .getObjectMetadata(profileId, bucketName, objectName);
    }
}