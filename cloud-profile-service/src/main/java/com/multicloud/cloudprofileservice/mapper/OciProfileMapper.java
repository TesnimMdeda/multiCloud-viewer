package com.multicloud.cloudprofileservice.mapper;

import com.multicloud.cloudprofile.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.entity.OciProfileDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OciProfileMapper {

    @Mapping(target = "provider",    source = "provider")
    @Mapping(target = "tenancyOcid", source = "tenancyOcid")
    @Mapping(target = "userOcid",    source = "userOcid")
    @Mapping(target = "fingerprint", source = "fingerprint")
        // never map encryptedPrivateKey → response (security)
    CloudProfileResponse toResponse(OciProfileDetails profile);
}