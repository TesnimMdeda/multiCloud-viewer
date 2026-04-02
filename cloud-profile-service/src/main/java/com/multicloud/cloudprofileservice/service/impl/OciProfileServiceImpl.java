package com.multicloud.cloudprofileservice.service.impl;

import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.dto.response.ValidationResult;
import com.multicloud.cloudprofileservice.entity.CloudProfile;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.entity.OciProfileDetails;
import com.multicloud.cloudprofileservice.exception.ProfileNotFoundException;
import com.multicloud.cloudprofileservice.exception.UnauthorizedActionException;
import com.multicloud.cloudprofileservice.mapper.OciProfileMapper;
import com.multicloud.cloudprofileservice.repository.CloudProfileRepository;
import com.multicloud.cloudprofileservice.repository.OciProfileDetailsRepository;
import com.multicloud.cloudprofileservice.service.BucketFetcher;
import com.multicloud.cloudprofileservice.service.OciProfileService;
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
public class OciProfileServiceImpl implements OciProfileService {

    private final CloudProfileRepository      profileRepository;
    private final OciProfileDetailsRepository ociRepo;
    private final ValidatorFactory            validatorFactory;
    private final EncryptionService           encryptionService;
    private final OciProfileMapper            mapper;
    private final BucketFetcher               bucketFetcher;

    public OciProfileServiceImpl(
            CloudProfileRepository profileRepository,
            OciProfileDetailsRepository ociRepo,
            ValidatorFactory validatorFactory,
            EncryptionService encryptionService,
            OciProfileMapper mapper,
            @Qualifier("ociBucketFetcher") BucketFetcher bucketFetcher) {
        this.profileRepository = profileRepository;
        this.ociRepo           = ociRepo;
        this.validatorFactory  = validatorFactory;
        this.encryptionService = encryptionService;
        this.mapper            = mapper;
        this.bucketFetcher     = bucketFetcher;
    }

    @Override
    public CloudProfileResponse createProfile(OciProfileRequest request, String ownerId) {

        // 1. Validate credentials via OCI SDK
        ValidationResult result = validatorFactory
                .getValidator(CloudProvider.OCI)
                .validate(request);

        String resolvedCompartmentId =
                (request.getCompartmentId() != null && !request.getCompartmentId().isBlank())
                        ? request.getCompartmentId()
                        : request.getTenancyOcid();

        // 2. Persist base profile
        CloudProfile profile = CloudProfile.builder()
                .profileName(request.getProfileName())
                .provider(CloudProvider.OCI)
                .region(request.getRegion())
                .ownerId(ownerId)
                .status(result.isValid()
                        ? CloudProfile.ProfileStatus.VALID
                        : CloudProfile.ProfileStatus.INVALID)
                .lastValidatedAt(LocalDateTime.now())
                .build();

        profileRepository.save(profile);

        // 3. Encrypt and persist OCI details
        String encryptedKey = encryptionService.encrypt(
                readMultipartAsString(request.getPrivateKey()));

        Map<String, String> details = result.getExtractedDetails();
        ociRepo.save(OciProfileDetails.builder()
                .profile(profile)
                .tenancyOcid(request.getTenancyOcid())
                .userOcid(request.getUserOcid())
                .fingerprint(request.getFingerprint())
                .tenancyName(details.get("tenancyName"))
                .homeRegion(details.get("homeRegion"))
                .compartmentId(resolvedCompartmentId)
                .encryptedPrivateKey(encryptedKey)
                .build());

        details.put("compartmentId", resolvedCompartmentId);

        // 4. Fetch real buckets to confirm permissions (never fails the creation)
        List<String> buckets = bucketFetcher.fetchBuckets(profile.getId());
        String connectionMessage = buckets.isEmpty()
                ? "Connected — no buckets found in this compartment"
                : "Connected — " + buckets.size() + " bucket(s) found";

        log.info("OCI profile {} created for owner {}. Buckets found: {}",
                profile.getId(), ownerId, buckets.size());

        return mapper.toResponse(profile, details, buckets, connectionMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CloudProfileResponse> getAllProfiles(String ownerId) {
        return profileRepository
                .findByOwnerIdAndProvider(ownerId, CloudProvider.OCI)
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
    public void deleteProfile(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        profileRepository.delete(profile);
        log.info("OCI profile {} deleted by owner {}", profileId, ownerId);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CloudProfile findAndVerifyOwner(String profileId, String ownerId) {
        CloudProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId));
        if (!profile.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedActionException("You do not own this profile");
        }
        if (profile.getProvider() != CloudProvider.OCI) {
            throw new IllegalArgumentException("Profile " + profileId + " is not an OCI profile");
        }
        return profile;
    }

    private Map<String, String> buildDetails(String profileId) {
        return ociRepo.findByProfileId(profileId)
                .map(d -> Map.of(
                        "tenancyOcid",   d.getTenancyOcid(),
                        "userOcid",      d.getUserOcid(),
                        "fingerprint",   d.getFingerprint(),
                        "tenancyName",   d.getTenancyName()   != null ? d.getTenancyName()  : "",
                        "homeRegion",    d.getHomeRegion()    != null ? d.getHomeRegion()   : "",
                        "compartmentId", d.getCompartmentId() != null ? d.getCompartmentId(): ""
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