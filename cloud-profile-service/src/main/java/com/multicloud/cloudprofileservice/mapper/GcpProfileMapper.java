package com.multicloud.cloudprofileservice.mapper;

import com.multicloud.cloudprofile.dto.response.CloudProfileResponse;
import com.multicloud.cloudprofileservice.entity.GcpProfileDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GcpProfileMapper {

    @Mapping(target = "provider",          source = "provider")
    @Mapping(target = "projectId",         source = "projectId")
    @Mapping(target = "serviceAccountEmail", source = "serviceAccountEmail")
        // never map encryptedServiceAccountKey → response (security)
    CloudProfileResponse toResponse(GcpProfileDetails profile);
}