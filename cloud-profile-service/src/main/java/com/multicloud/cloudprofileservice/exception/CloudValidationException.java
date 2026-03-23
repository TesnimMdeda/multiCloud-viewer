package com.multicloud.cloudprofileservice.exception;

public class CloudValidationException extends RuntimeException {

    private final String provider;

    public CloudValidationException(String provider, String message) {
        super("[" + provider.toUpperCase() + "] " + message);
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}