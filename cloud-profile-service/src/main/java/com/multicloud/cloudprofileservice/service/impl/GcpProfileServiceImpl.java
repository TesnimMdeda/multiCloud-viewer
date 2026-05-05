package com.multicloud.cloudprofileservice.service.impl;

import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.dto.response.ValidationResult;
import com.multicloud.cloudprofileservice.entity.CloudProfile;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.entity.GcpProfileDetails;
import com.multicloud.cloudprofileservice.exception.ProfileNotFoundException;
import com.multicloud.cloudprofileservice.exception.UnauthorizedActionException;
import com.multicloud.cloudprofileservice.mapper.GcpProfileMapper;
import com.multicloud.cloudprofileservice.repository.CloudProfileRepository;
import com.multicloud.cloudprofileservice.repository.GcpProfileDetailsRepository;
import com.multicloud.cloudprofileservice.service.BucketFetcher;
import com.multicloud.cloudprofileservice.service.GcpProfileService;
import com.multicloud.cloudprofileservice.validator.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
public class GcpProfileServiceImpl implements GcpProfileService {

    private final CloudProfileRepository      profileRepository;
    private final GcpProfileDetailsRepository gcpRepo;
    private final ValidatorFactory            validatorFactory;
    private final EncryptionService           encryptionService;
    private final GcpProfileMapper            mapper;
    private final BucketFetcher               bucketFetcher;
    private final com.multicloud.cloudprofileservice.client.NotificationClient notificationClient;

    public GcpProfileServiceImpl(
            CloudProfileRepository profileRepository,
            GcpProfileDetailsRepository gcpRepo,
            ValidatorFactory validatorFactory,
            EncryptionService encryptionService,
            GcpProfileMapper mapper,
            @Qualifier("gcsBucketFetcher") BucketFetcher bucketFetcher,
            com.multicloud.cloudprofileservice.client.NotificationClient notificationClient) {
        this.profileRepository = profileRepository;
        this.gcpRepo           = gcpRepo;
        this.validatorFactory  = validatorFactory;
        this.encryptionService = encryptionService;
        this.mapper            = mapper;
        this.bucketFetcher     = bucketFetcher;
        this.notificationClient = notificationClient;
    }

    @Override
    public CloudProfileResponse createProfile(GcpProfileRequest request, String ownerId) {

        // 1. Validate credentials via GCP SDK
        ValidationResult result = validatorFactory
                .getValidator(CloudProvider.GCP)
                .validate(request);

        // 2. Persist base profile
        CloudProfile profile = CloudProfile.builder()
                .profileName(request.getProfileName())
                .provider(CloudProvider.GCP)
                .region(request.getRegion())
                .ownerId(ownerId)
                .status(result.isValid()
                        ? CloudProfile.ProfileStatus.VALID
                        : CloudProfile.ProfileStatus.INVALID)
                .lastValidatedAt(LocalDateTime.now())
                .build();

        profileRepository.save(profile);

        // 3. Encrypt and persist GCP details
        String encryptedKey = encryptionService.encrypt(
                readMultipartAsString(request.getServiceAccountKey()));

        Map<String, String> details = result.getExtractedDetails();
        gcpRepo.save(GcpProfileDetails.builder()
                .profile(profile)
                .projectId(request.getProjectId())
                .serviceAccountEmail(details.get("serviceAccountEmail"))
                .clientId(details.get("clientId"))
                .keyType("service_account")
                .tokenUri(details.get("tokenUri"))
                .encryptedServiceAccountKey(encryptedKey)
                .build());

        // 4. Fetch real buckets to confirm permissions (never fails the creation)
        List<String> buckets = bucketFetcher.fetchBuckets(profile.getId());
        String connectionMessage = buckets.isEmpty()
                ? "Connected — no buckets found in this project"
                : "Connected — " + buckets.size() + " bucket(s) found";

        log.info("GCP profile {} created for owner {}. Buckets found: {}",
                profile.getId(), ownerId, buckets.size());

        // 5. Send Notification
        try {
            notificationClient.sendNotification(com.multicloud.cloudprofileservice.client.NotificationClient.NotificationRequest.builder()
                    .userEmail(ownerId)
                    .title("Cloud Profile Created")
                    .message("Profile '" + profile.getProfileName() + "' is now " + profile.getStatus().name() + " and active.")
                    .type(profile.getStatus() == CloudProfile.ProfileStatus.VALID ? "SUCCESS" : "ERROR")
                    .build());
        } catch (Exception e) {
            log.error("Failed to send creation notification for profile {}", profile.getId(), e);
        }

        return mapper.toResponse(profile, details, buckets, connectionMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CloudProfileResponse> getAllProfiles(String ownerId) {
        return profileRepository
                .findByOwnerIdAndProvider(ownerId, CloudProvider.GCP)
                .stream()
                .map(p -> mapper.toResponse(p, buildDetails(p.getId()), null, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CloudProfileResponse getProfileById(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        return mapper.toResponse(profile, buildDetails(profileId), null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public com.multicloud.cloudprofileservice.dto.response.GcpProfileDetailsResponse getGcpProfileDetails(String profileId, String ownerId) {
        findAndVerifyOwner(profileId, ownerId);
        GcpProfileDetails details = gcpRepo.findByProfileId(profileId)
                .orElseThrow(() -> new ProfileNotFoundException("GCP details not found for profile " + profileId));

        return com.multicloud.cloudprofileservice.dto.response.GcpProfileDetailsResponse.builder()
                .profileId(profileId)
                .projectId(details.getProjectId())
                .serviceAccountEmail(details.getServiceAccountEmail())
                .clientId(details.getClientId())
                .keyType(details.getKeyType())
                .tokenUri(details.getTokenUri())
                .decryptedServiceAccountKey(encryptionService.decrypt(details.getEncryptedServiceAccountKey()))
                .build();
    }

    @Override
    public void deleteProfile(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        profileRepository.delete(profile);
        log.info("GCP profile {} deleted by owner {}", profileId, ownerId);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CloudProfile findAndVerifyOwner(String profileId, String ownerId) {
        CloudProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId));
        if (!profile.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedActionException("You do not own this profile");
        }
        if (profile.getProvider() != CloudProvider.GCP) {
            throw new IllegalArgumentException("Profile " + profileId + " is not a GCP profile");
        }
        return profile;
    }

    private Map<String, String> buildDetails(String profileId) {
        return gcpRepo.findByProfileId(profileId)
                .map(d -> Map.of(
                        "projectId",           d.getProjectId(),
                        "serviceAccountEmail", d.getServiceAccountEmail(),
                        "clientId",            d.getClientId(),
                        "keyType",             d.getKeyType(),
                        "tokenUri",            d.getTokenUri() != null ? d.getTokenUri() : ""
                ))
                .orElse(Map.of());
    }

    private String readMultipartAsString(org.springframework.web.multipart.MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file.getOriginalFilename(), e);
        }
    }
}