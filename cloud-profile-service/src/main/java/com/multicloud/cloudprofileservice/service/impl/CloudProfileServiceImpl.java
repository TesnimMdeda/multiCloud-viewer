package com.multicloud.cloudprofileservice.service.impl;

import com.multicloud.cloudprofileservice.dto.request.CreateGcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.request.CreateOciProfileRequest;
import com.multicloud.cloudprofile.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.entity.*;
import com.multicloud.cloudprofile.exception.CloudValidationException;
import com.multicloud.cloudprofile.exception.ProfileNotFoundException;
import com.multicloud.cloudprofile.mapper.GcpProfileMapper;
import com.multicloud.cloudprofile.mapper.OciProfileMapper;
import com.multicloud.cloudprofile.repository.GcpProfileRepository;
import com.multicloud.cloudprofile.repository.OciProfileRepository;
import com.multicloud.cloudprofile.service.CloudProfileService;
import com.multicloud.cloudprofile.service.CloudValidationService.ValidationResult;
import com.multicloud.cloudprofile.util.EncryptionUtil;
import com.multicloud.cloudprofile.util.ValidationStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CloudProfileServiceImpl implements CloudProfileService {

    private final GcpProfileRepository      gcpRepository;
    private final OciProfileRepository      ociRepository;
    private final GcpProfileMapper          gcpMapper;
    private final OciProfileMapper          ociMapper;
    private final EncryptionUtil            encryptionUtil;
    private final ValidationStrategyFactory validationFactory;

    @Override
    public CloudProfileResponse createGcpProfile(
            CreateGcpProfileRequest request, String userId) {

        GcpProfile profile = new GcpProfile();
        profile.setProfileName(request.getProfileName());
        profile.setProjectId(request.getProjectId());
        profile.setRegion(request.getRegion());
        profile.setProvider(CloudProvider.GCP);
        profile.setStatus(ProfileStatus.PENDING);
        profile.setCreatedByUserId(userId);

        // Encrypt key before storing
        profile.setEncryptedServiceAccountKey(
                encryptionUtil.encrypt(request.getServiceAccountKeyJson()));

        // Validate against GCP API
        profile = gcpRepository.save(profile);
        profile = validateAndEnrich(profile, gcpRepository.save(profile));

        log.info("GCP profile created: {} for user: {}", profile.getId(), userId);
        return gcpMapper.toResponse(profile);
    }

    @Override
    public CloudProfileResponse createOciProfile(
            CreateOciProfileRequest request, String userId) {

        OciProfile profile = new OciProfile();
        profile.setProfileName(request.getProfileName());
        profile.setRegion(request.getRegion());
        profile.setTenancyOcid(request.getTenancyOcid());
        profile.setUserOcid(request.getUserOcid());
        profile.setFingerprint(request.getFingerprint());
        profile.setProvider(CloudProvider.OCI);
        profile.setStatus(ProfileStatus.PENDING);
        profile.setCreatedByUserId(userId);

        // Encrypt PEM key before storing
        profile.setEncryptedPrivateKey(
                encryptionUtil.encrypt(request.getPrivateKeyPem()));

        profile = ociRepository.save(profile);
        profile = validateAndEnrich(profile, ociRepository.save(profile));

        log.info("OCI profile created: {} for user: {}", profile.getId(), userId);
        return ociMapper.toResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public CloudProfileResponse getById(UUID id, String userId) {
        return findAndMap(id, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CloudProfileResponse> getAllByUser(String userId) {
        List<CloudProfileResponse> gcpProfiles = gcpRepository
                .findByCreatedByUserId(userId)
                .stream()
                .map(gcpMapper::toResponse)
                .collect(Collectors.toList());

        List<CloudProfileResponse> ociProfiles = ociRepository
                .findByCreatedByUserId(userId)
                .stream()
                .map(ociMapper::toResponse)
                .collect(Collectors.toList());

        return Stream.concat(gcpProfiles.stream(), ociProfiles.stream())
                .collect(Collectors.toList());
    }

    @Override
    public CloudProfileResponse revalidate(UUID id, String userId) {
        return findAndMap(id, userId); // validation re-triggered inside
    }

    @Override
    public void delete(UUID id, String userId) {
        gcpRepository.findById(id).ifPresent(p -> {
            checkOwnership(p, userId);
            gcpRepository.delete(p);
        });
        ociRepository.findById(id).ifPresent(p -> {
            checkOwnership(p, userId);
            ociRepository.delete(p);
        });
    }

    // ─── private helpers ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T extends CloudProfile> T validateAndEnrich(T profile, T saved) {
        try {
            var validator = validationFactory.getValidator(saved.getProvider());
            ValidationResult result = validator.validate(saved);

            saved.setStatus(result.valid() ? ProfileStatus.ACTIVE : ProfileStatus.INVALID);
            saved.setValidationMessage(result.message());
            saved.setLastValidatedAt(LocalDateTime.now());

            if (result.valid()) {
                if (saved instanceof GcpProfile gcp) {
                    gcp.setServiceAccountEmail(result.extractedMetadata1());
                    gcp.setProjectNumber(result.extractedMetadata2());
                } else if (saved instanceof OciProfile oci) {
                    oci.setTenancyName(result.extractedMetadata1());
                    oci.setHomeRegion(result.extractedMetadata2());
                }
            }
        } catch (CloudValidationException e) {
            saved.setStatus(ProfileStatus.INVALID);
            saved.setValidationMessage(e.getMessage());
            saved.setLastValidatedAt(LocalDateTime.now());
            throw e; // re-throw so controller returns 422
        }

        if (saved instanceof GcpProfile gcp) {
            return (T) gcpRepository.save(gcp);
        } else {
            return (T) ociRepository.save((OciProfile) saved);
        }
    }

    private CloudProfileResponse findAndMap(UUID id, String userId) {
        var gcpOpt = gcpRepository.findById(id);
        if (gcpOpt.isPresent()) {
            checkOwnership(gcpOpt.get(), userId);
            return gcpMapper.toResponse(gcpOpt.get());
        }
        var ociOpt = ociRepository.findById(id);
        if (ociOpt.isPresent()) {
            checkOwnership(ociOpt.get(), userId);
            return ociMapper.toResponse(ociOpt.get());
        }
        throw new ProfileNotFoundException(id.toString());
    }

    private void checkOwnership(CloudProfile profile, String userId) {
        if (!profile.getCreatedByUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not own this profile");
        }
    }
}