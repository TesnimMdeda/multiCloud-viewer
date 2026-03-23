package com.multicloud.cloudprofileservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OciProfileRequest {

    @NotBlank(message = "Profile name is required")
    private String profileName;

    @NotBlank(message = "Region is required")
    private String region;

    @NotBlank(message = "Tenancy OCID is required")
    @Pattern(regexp = "^ocid1\\.tenancy\\.oc1\\..+$",
            message = "Invalid tenancy OCID format")
    private String tenancyOcid;

    @NotBlank(message = "User OCID is required")
    @Pattern(regexp = "^ocid1\\.user\\.oc1\\..+$",
            message = "Invalid user OCID format")
    private String userOcid;

    @NotBlank(message = "Fingerprint is required")
    @Pattern(regexp = "^([0-9a-f]{2}:){15}[0-9a-f]{2}$",
            message = "Invalid fingerprint format (expected xx:xx:...:xx)")
    private String fingerprint;

    @NotBlank(message = "Private key is required")
    private String privateKeyPem; // raw PEM content from uploaded file
}