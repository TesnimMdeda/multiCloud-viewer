package com.multicloud.cloudprofileservice.validator;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.cloud.resourcemanager.v3.ProjectsSettings;
import com.multicloud.cloudprofileservice.dto.request.GcpProfileRequest;
import com.multicloud.cloudprofileservice.dto.response.ValidationResult;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.exception.CloudValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class GcpCredentialValidator implements CloudCredentialValidator {

    @Override
    public CloudProvider getSupportedProvider() { return CloudProvider.GCP; }

    @Override
    public ValidationResult validate(Object req) throws CloudValidationException {
        GcpProfileRequest request = (GcpProfileRequest) req;
        try {
            byte[] keyBytes = request.getServiceAccountKey().getBytes();

            // ── Step 1: Parse the service account JSON ─────────────────────
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(keyBytes));

            // Verify it is a service account (not user credentials)
            if (!(credentials instanceof ServiceAccountCredentials sac)) {
                throw new CloudValidationException(
                        "The provided key is not a service account key");
            }

            // ── Step 2: Call Resource Manager to verify project ─────────────
            credentials = credentials.createScoped(
                    "https://www.googleapis.com/auth/cloud-platform.read-only");

            ProjectsSettings settings = ProjectsSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            try (ProjectsClient projectsClient = ProjectsClient.create(settings)) {
                // getProject throws NOT_FOUND or PERMISSION_DENIED if key is invalid
                var project = projectsClient.getProject("projects/" + request.getProjectId());

                // ── Step 3: Extract details from the project and key ─────────
                Map<String, String> details = new HashMap<>();
                details.put("projectId", request.getProjectId());
                details.put("projectDisplayName", project.getDisplayName());
                details.put("serviceAccountEmail", sac.getClientEmail());
                details.put("clientId", sac.getClientId());
                details.put("keyType", "service_account");
                details.put("tokenUri", sac.getTokenServerUri().toString());

                log.info("GCP validation OK — project: {}, sa: {}",
                        request.getProjectId(), sac.getClientEmail());

                return ValidationResult.builder()
                        .valid(true)
                        .message("GCP credentials validated successfully")
                        .extractedDetails(details)
                        .build();
            }

        } catch (CloudValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GCP validation failed: {}", e.getMessage());
            throw new CloudValidationException("GCP validation failed: " + e.getMessage());
        }
    }
}