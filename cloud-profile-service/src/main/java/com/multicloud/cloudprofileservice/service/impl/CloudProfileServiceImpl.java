package com.multicloud.cloudprofileservice.service.impl;

import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.dto.response.ValidationResult;
import com.multicloud.cloudprofileservice.entity.*;
import com.multicloud.cloudprofileservice.exception.ProfileNotFoundException;
import com.multicloud.cloudprofileservice.exception.UnauthorizedActionException;
import com.multicloud.cloudprofileservice.mapper.CloudProfileMapper;
import com.multicloud.cloudprofileservice.repository.CloudProfileRepository;
import com.multicloud.cloudprofileservice.repository.GcpProfileDetailsRepository;
import com.multicloud.cloudprofileservice.repository.OciProfileDetailsRepository;
import com.multicloud.cloudprofileservice.service.CloudProfileService;
import com.multicloud.cloudprofileservice.validator.ValidatorFactory;
import com.multicloud.cloudprofileservice.entity.GcpProfileDetails;
import com.multicloud.cloudprofileservice.entity.OciProfileDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class CloudProfileServiceImpl implements CloudProfileService {

    private final CloudProfileRepository   profileRepository;
    private final GcpProfileDetailsRepository gcpRepo;
    private final OciProfileDetailsRepository ociRepo;
    private final ValidatorFactory         validatorFactory;
    private final EncryptionService encryptionService;
    private final CloudProfileMapper       mapper;

    // ═══════════════════════════════════════════════════════════════
    // CREATE GCP PROFILE
    // Workflow: validate → extract details → encrypt key → save
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CloudProfileResponse createGcpProfile(GcpProfileRequest request, String ownerId) {

        // 1. Validate credentials against real GCP SDK (throws on failure)
        ValidationResult result = validatorFactory
                .getValidator(CloudProvider.GCP)
                .validate(request);

        // 2. Create base profile record
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

        // 3. Encrypt service account key before persisting
        String rawKey = readMultipartAsString(request.getServiceAccountKey());
        String encryptedKey = encryptionService.encrypt(rawKey);

        // 4. Save GCP-specific details (auto-extracted by SDK)
        Map<String, String> details = result.getExtractedDetails();
        GcpProfileDetails gcpDetails = GcpProfileDetails.builder()
                .profile(profile)
                .projectId(request.getProjectId())
                .serviceAccountEmail(details.get("serviceAccountEmail"))
                .clientId(details.get("clientId"))
                .keyType("service_account")
                .tokenUri(details.get("tokenUri"))
                .encryptedServiceAccountKey(encryptedKey)
                .build();

        gcpRepo.save(gcpDetails);

        log.info("GCP profile created: {} for owner: {}", profile.getId(), ownerId);
        return mapper.toResponse(profile, details);
    }

    // ═══════════════════════════════════════════════════════════════
    // CREATE OCI PROFILE
    // ═══════════════════════════════════════════════════════════════

    @Override
    public CloudProfileResponse createOciProfile(OciProfileRequest request, String ownerId) {

        // 1. Validate credentials against real OCI SDK (throws on failure)
        ValidationResult result = validatorFactory
                .getValidator(CloudProvider.OCI)
                .validate(request);

        // 2. Create base profile record
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

        // 3. Encrypt private key before persisting
        String encryptedKey = encryptionService.encrypt(
                readMultipartAsString(request.getPrivateKey()));

        // 4. Save OCI-specific details
        Map<String, String> details = result.getExtractedDetails();
        OciProfileDetails ociDetails = OciProfileDetails.builder()
                .profile(profile)
                .tenancyOcid(request.getTenancyOcid())
                .userOcid(request.getUserOcid())
                .fingerprint(request.getFingerprint())
                .tenancyName(details.get("tenancyName"))
                .homeRegion(details.get("homeRegion"))
                .compartmentId(request.getTenancyOcid()) // root compartment
                .encryptedPrivateKey(encryptedKey)
                .build();

        ociRepo.save(ociDetails);

        log.info("OCI profile created: {} for owner: {}", profile.getId(), ownerId);
        return mapper.toResponse(profile, details);
    }

    // ═══════════════════════════════════════════════════════════════
    // LIST PROFILES
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<CloudProfileResponse> getProfilesByOwner(String ownerId) {
        return profileRepository.findByOwnerId(ownerId).stream()
                .map(p -> mapper.toResponse(p, Map.of()))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // DELETE PROFILE
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void deleteProfile(String profileId, String ownerId) {
        CloudProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId));

        if (!profile.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedActionException("You do not own this profile");
        }

        // Detail rows cascade-delete via ON DELETE CASCADE in DB schema
        profileRepository.delete(profile);
        log.info("Profile {} deleted by owner {}", profileId, ownerId);
    }

    // ─── private helpers ────────────────────────────────────────────

    private String readMultipartAsString(org.springframework.web.multipart.MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file: " + file.getOriginalFilename(), e);
        }
    }
}