package com.multicloud.resourcemanagementservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GcpProfileDetailsResponse {
    private String profileId;
    private String projectId;
    private String serviceAccountEmail;
    private String clientId;
    private String keyType;
    private String tokenUri;
    private String decryptedServiceAccountKey;
}
