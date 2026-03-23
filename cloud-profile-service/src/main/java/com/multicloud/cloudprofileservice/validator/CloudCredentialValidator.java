package com.multicloud.cloudprofileservice.validator;

import com.multicloud.cloudprofileservice.dto.response.ValidationResult;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.exception.CloudValidationException;

public interface CloudCredentialValidator {

    /**
     * Returns the provider this validator handles.
     */
    CloudProvider getSupportedProvider();

    /**
     * Validates the credentials against the real cloud SDK.
     * Returns extracted details (project name, tenancy name, etc.)
     * or throws CloudValidationException on failure.
     */
    ValidationResult validate(Object request) throws CloudValidationException;
}