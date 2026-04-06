package com.multicloud.resourcemanagementservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OciProfileDetailsResponse {
    private String tenancyOcid;
    private String userOcid;
    private String fingerprint;
    private String region;
    private String compartmentId;
    private String decryptedPrivateKey;
}
