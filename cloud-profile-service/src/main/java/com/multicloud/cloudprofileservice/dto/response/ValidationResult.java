package com.multicloud.cloudprofileservice.dto.response;

import lombok.*;
import java.util.Map;

@Data
@Builder
public class ValidationResult {

    private boolean valid;
    private String message;
    private Map<String, String> extractedDetails; // auto-fetched from SDK
}