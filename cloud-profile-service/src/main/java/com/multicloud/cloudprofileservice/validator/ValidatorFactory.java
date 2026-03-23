package com.multicloud.cloudprofileservice.validator;

import com.multicloud.cloudprofileservice.entity.CloudProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Automatically collects all CloudCredentialValidator beans.
 * Adding a new provider = add a new @Component validator. Zero changes here.
 */
@Component
@RequiredArgsConstructor
public class ValidatorFactory {

    private final Map<CloudProvider, CloudCredentialValidator> validators;

    // Spring injects all CloudCredentialValidator beans automatically
    public ValidatorFactory(List<CloudCredentialValidator> validatorList) {
        this.validators = validatorList.stream()
                .collect(Collectors.toMap(
                        CloudCredentialValidator::getSupportedProvider,
                        Function.identity()
                ));
    }

    public CloudCredentialValidator getValidator(CloudProvider provider) {
        var validator = validators.get(provider);
        if (validator == null) {
            throw new UnsupportedOperationException(
                    "No validator found for provider: " + provider);
        }
        return validator;
    }
}
