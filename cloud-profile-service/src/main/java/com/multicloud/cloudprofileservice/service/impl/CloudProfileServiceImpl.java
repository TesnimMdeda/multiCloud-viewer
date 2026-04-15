package com.multicloud.cloudprofileservice.service.impl;

import com.multicloud.cloudprofileservice.dto.response.AllCloudProfilesResponse;
import com.multicloud.cloudprofileservice.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.entity.CloudProfile;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.mapper.GcpProfileMapper;
import com.multicloud.cloudprofileservice.mapper.OciProfileMapper;
import com.multicloud.cloudprofileservice.repository.CloudProfileRepository;
import com.multicloud.cloudprofileservice.repository.GcpProfileDetailsRepository;
import com.multicloud.cloudprofileservice.repository.OciProfileDetailsRepository;
import com.multicloud.cloudprofileservice.service.CloudProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CloudProfileServiceImpl implements CloudProfileService {

    private final CloudProfileRepository profileRepository;
    private final GcpProfileDetailsRepository gcpRepo;
    private final OciProfileDetailsRepository ociRepo;
    private final GcpProfileMapper gcpMapper;
    private final OciProfileMapper ociMapper;

    @Override
    public AllCloudProfilesResponse getAllCloudProfiles(String ownerId) {
        List<CloudProfile> profiles = profileRepository.findByOwnerId(ownerId);

        List<CloudProfileResponse> responseList = profiles.stream()
                .map(this::mapToResponse)
                .toList();

        Map<String, Long> countByProvider = profiles.stream()
                .collect(Collectors.groupingBy(p -> p.getProvider().name(), Collectors.counting()));

        return AllCloudProfilesResponse.builder()
                .profiles(responseList)
                .totalCount(profiles.size())
                .countByProvider(countByProvider)
                .build();
    }

    private CloudProfileResponse mapToResponse(CloudProfile profile) {
        if (profile.getProvider() == CloudProvider.GCP) {
            Map<String, String> details = gcpRepo.findByProfileId(profile.getId())
                    .map(d -> Map.of(
                            "projectId", d.getProjectId(),
                            "serviceAccountEmail", d.getServiceAccountEmail(),
                            "clientId", d.getClientId(),
                            "keyType", d.getKeyType(),
                            "tokenUri", d.getTokenUri() != null ? d.getTokenUri() : ""
                    ))
                    .orElse(Map.of());
            return gcpMapper.toResponse(profile, details, null, null);
        } else if (profile.getProvider() == CloudProvider.OCI) {
            Map<String, String> details = ociRepo.findByProfileId(profile.getId())
                    .map(d -> Map.of(
                            "tenancyOcid", d.getTenancyOcid(),
                            "userOcid", d.getUserOcid(),
                            "fingerprint", d.getFingerprint(),
                            "tenancyName", d.getTenancyName() != null ? d.getTenancyName() : "",
                            "homeRegion", d.getHomeRegion() != null ? d.getHomeRegion() : "",
                            "compartmentId", d.getCompartmentId() != null ? d.getCompartmentId() : ""
                    ))
                    .orElse(Map.of());
            return ociMapper.toResponse(profile, details, null, null);
        }
        
        // Fallback for unknown provider
        return CloudProfileResponse.builder()
                .id(profile.getId())
                .profileName(profile.getProfileName())
                .provider(profile.getProvider().name())
                .region(profile.getRegion())
                .status(profile.getStatus().name())
                .ownerId(profile.getOwnerId())
                .build();
    }
}
