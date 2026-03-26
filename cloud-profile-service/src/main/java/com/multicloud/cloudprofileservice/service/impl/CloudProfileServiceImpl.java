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

    private final CloudProfileRepository      profileRepository;
    private final GcpProfileDetailsRepository gcpRepo;
    private final OciProfileDetailsRepository ociRepo;
    private final ValidatorFactory            validatorFactory;
    private final EncryptionService           encryptionService;
    private final CloudProfileMapper          mapper;

    // ═══════════════════════════════════════════════════════════════
    // CREATE GCP PROFILE
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

        // 4. Save GCP-specific details
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

        // 2. Resolve compartment: use provided value or fall back to root (tenancy)
        String resolvedCompartmentId =
                (request.getCompartmentId() != null && !request.getCompartmentId().isBlank())
                        ? request.getCompartmentId()
                        : request.getTenancyOcid();

        // 3. Create base profile record
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


        String encryptedKey = encryptionService.encrypt(
                readMultipartAsString(request.getPrivateKey()));


        Map<String, String> details = result.getExtractedDetails();
        OciProfileDetails ociDetails = OciProfileDetails.builder()
                .profile(profile)
                .tenancyOcid(request.getTenancyOcid())
                .userOcid(request.getUserOcid())
                .fingerprint(request.getFingerprint())
                .tenancyName(details.get("tenancyName"))
                .homeRegion(details.get("homeRegion"))
                .compartmentId(resolvedCompartmentId)
                .encryptedPrivateKey(encryptedKey)
                .build();

        ociRepo.save(ociDetails);

        // Expose compartmentId in response details
        details.put("compartmentId", resolvedCompartmentId);

        log.info("OCI profile created: {} for owner: {}", profile.getId(), ownerId);
        return mapper.toResponse(profile, details);
    }

    // ═══════════════════════════════════════════════════════════════
    // LIST ALL PROFILES
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<CloudProfileResponse> getProfilesByOwner(String ownerId) {
        return profileRepository.findByOwnerId(ownerId).stream()
                .map(p -> mapper.toResponse(p, Map.of()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CloudProfileResponse> getGcpProfilesByOwner(String ownerId) {
        return profileRepository
                .findByOwnerIdAndProvider(ownerId, CloudProvider.GCP)
                .stream()
                .map(p -> mapper.toResponse(p, buildGcpDetails(p.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CloudProfileResponse> getOciProfilesByOwner(String ownerId) {
        return profileRepository
                .findByOwnerIdAndProvider(ownerId, CloudProvider.OCI)
                .stream()
                .map(p -> mapper.toResponse(p, buildOciDetails(p.getId())))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // GET BY ID
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public CloudProfileResponse getProfileById(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        Map<String, String> details = switch (profile.getProvider()) {
            case GCP -> buildGcpDetails(profileId);
            case OCI -> buildOciDetails(profileId);
        };
        return mapper.toResponse(profile, details);
    }

    @Override
    @Transactional(readOnly = true)
    public CloudProfileResponse getGcpProfileById(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        assertProvider(profile, CloudProvider.GCP);
        return mapper.toResponse(profile, buildGcpDetails(profileId));
    }

    @Override
    @Transactional(readOnly = true)
    public CloudProfileResponse getOciProfileById(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        assertProvider(profile, CloudProvider.OCI);
        return mapper.toResponse(profile, buildOciDetails(profileId));
    }

    // ═══════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void deleteProfile(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        profileRepository.delete(profile);
        log.info("Profile {} deleted by owner {}", profileId, ownerId);
    }

    @Override
    public void deleteGcpProfile(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        assertProvider(profile, CloudProvider.GCP);
        profileRepository.delete(profile);
        log.info("GCP profile {} deleted by owner {}", profileId, ownerId);
    }

    @Override
    public void deleteOciProfile(String profileId, String ownerId) {
        CloudProfile profile = findAndVerifyOwner(profileId, ownerId);
        assertProvider(profile, CloudProvider.OCI);
        profileRepository.delete(profile);
        log.info("OCI profile {} deleted by owner {}", profileId, ownerId);
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private CloudProfile findAndVerifyOwner(String profileId, String ownerId) {
        CloudProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId));
        if (!profile.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedActionException("You do not own this profile");
        }
        return profile;
    }

    private void assertProvider(CloudProfile profile, CloudProvider expected) {
        if (profile.getProvider() != expected) {
            throw new IllegalArgumentException(
                    "Profile " + profile.getId() + " is not a " + expected + " profile");
        }
    }

    /**
     * Build safe (non-sensitive) GCP details map from stored metadata.
     * Never exposes the encrypted key.
     */
    private Map<String, String> buildGcpDetails(String profileId) {
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

    /**
     * Build safe (non-sensitive) OCI details map from stored metadata.
     * Never exposes the encrypted private key.
     */
    private Map<String, String> buildOciDetails(String profileId) {
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
            throw new RuntimeException("Failed to read uploaded file: " + file.getOriginalFilename(), e);
        }
    }
}