package com.multicloud.cloudprofileservice.util;

import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.service.CloudValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ValidationStrategyFactory {

    // Spring injects all CloudValidationService beans by their provider key
    private final Map<String, CloudValidationService> validators;

    /**
     * Returns the correct validator for the given provider.
     * Bean names must match: "gcpValidationServiceImpl", "ociValidationServiceImpl"
     */
    public CloudValidationService getValidator(CloudProvider provider) {
        String beanKey = switch (provider) {
            case GCP -> "gcpValidationServiceImpl";
            case OCI -> "ociValidationServiceImpl";
        };
        CloudValidationService service = validators.get(beanKey);
        if (service == null) {
            throw new IllegalArgumentException(
                    "No validator registered for provider: " + provider);
        }
        return service;
    }
}