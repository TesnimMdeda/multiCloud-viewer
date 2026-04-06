package com.multicloud.cloudprofileservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GcpProfileDetailsResponse {
    private String profileId;
    private String projectId;
    private String serviceAccountEmail;
    private String clientId;
    private String keyType;
    private String tokenUri;
    private String decryptedServiceAccountKey;
}
