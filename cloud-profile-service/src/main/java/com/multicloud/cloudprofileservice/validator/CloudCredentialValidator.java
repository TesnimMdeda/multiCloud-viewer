package com.multicloud.cloudprofileservice.validator;

import com.multicloud.cloudprofileservice.dto.response.ValidationResult;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.exception.CloudValidationException;

public interface CloudCredentialValidator {

    CloudProvider getSupportedProvider();

    ValidationResult validate(Object request) throws CloudValidationException;
}