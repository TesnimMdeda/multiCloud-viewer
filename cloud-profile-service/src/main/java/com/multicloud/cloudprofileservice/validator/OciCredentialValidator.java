package com.multicloud.cloudprofileservice.validator;

import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.GetTenancyRequest;
import com.oracle.bmc.identity.requests.GetUserRequest;
import com.multicloud.cloudprofileservice.dto.request.OciProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.ValidationResult;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.exception.CloudValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class OciCredentialValidator implements CloudCredentialValidator {

    @Override
    public CloudProvider getSupportedProvider() { return CloudProvider.OCI; }

    @Override
    public ValidationResult validate(Object req) throws CloudValidationException {
        OciProfileRequest request = (OciProfileRequest) req;
        try {
            String privateKeyContent = new String(
                    request.getPrivateKey().getBytes(), StandardCharsets.UTF_8);

            var authProvider = SimpleAuthenticationDetailsProvider.builder()
                    .tenantId(request.getTenancyOcid())
                    .userId(request.getUserOcid())
                    .fingerprint(request.getFingerprint())
                    .region(com.oracle.bmc.Region.fromRegionId(request.getRegion()))
                    .privateKeySupplier(() -> new ByteArrayInputStream(
                            privateKeyContent.getBytes(StandardCharsets.UTF_8)))
                    .build();

            try (IdentityClient identityClient = IdentityClient.builder()
                    .build(authProvider)) {

                var tenancyResp = identityClient.getTenancy(
                        GetTenancyRequest.builder()
                                .tenancyId(request.getTenancyOcid())
                                .build());

                var userResp = identityClient.getUser(
                        GetUserRequest.builder()
                                .userId(request.getUserOcid())
                                .build());

                Map<String, String> details = new HashMap<>();
                details.put("tenancyOcid",  request.getTenancyOcid());
                details.put("tenancyName",  tenancyResp.getTenancy().getName());
                details.put("homeRegion",   tenancyResp.getTenancy().getHomeRegionKey());
                details.put("userEmail",    userResp.getUser().getEmail());
                details.put("userName",     userResp.getUser().getName());
                details.put("compartmentId", request.getTenancyOcid());

                log.info("OCI validation OK — tenancy: {}, user: {}",
                        tenancyResp.getTenancy().getName(),
                        userResp.getUser().getName());

                return ValidationResult.builder()
                        .valid(true)
                        .message("OCI credentials validated successfully")
                        .extractedDetails(details)
                        .build();
            }

        } catch (CloudValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OCI validation failed: {}", e.getMessage());
            throw new CloudValidationException("OCI validation failed: " + e.getMessage());
        }
    }
}