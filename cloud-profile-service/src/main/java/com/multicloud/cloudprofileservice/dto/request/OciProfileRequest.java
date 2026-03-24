package com.multicloud.cloudprofileservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class OciProfileRequest {

    @NotBlank(message = "Profile name is required")
    private String profileName;

    @NotBlank(message = "Tenancy OCID is required")
    @Pattern(regexp = "ocid1\\.tenancy\\.oc1\\..+",
            message = "Invalid Tenancy OCID format")
    private String tenancyOcid;

    @NotBlank(message = "User OCID is required")
    @Pattern(regexp = "ocid1\\.user\\.oc1\\..+",
            message = "Invalid User OCID format")
    private String userOcid;

    @NotBlank(message = "Region is required")
    private String region;

    @NotBlank(message = "API key fingerprint is required")
    @Pattern(regexp = "([0-9a-f]{2}:){15}[0-9a-f]{2}",
            message = "Invalid fingerprint format (aa:bb:cc:... expected)")
    private String fingerprint;

    @NotNull(message = "Private key file is required")
    private MultipartFile privateKey; // .pem file upload
}