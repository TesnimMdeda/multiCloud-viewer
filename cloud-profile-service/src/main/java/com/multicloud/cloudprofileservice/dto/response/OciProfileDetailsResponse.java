package com.multicloud.cloudprofileservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OciProfileDetailsResponse {
    private String tenancyOcid;
    private String userOcid;
    private String fingerprint;
    private String region;
    private String compartmentId;
    private String decryptedPrivateKey;
}
