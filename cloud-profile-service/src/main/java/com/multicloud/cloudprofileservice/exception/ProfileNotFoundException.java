package com.multicloud.cloudprofileservice.exception;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String id) {
        super("Cloud profile not found with id: " + id);
    }
}