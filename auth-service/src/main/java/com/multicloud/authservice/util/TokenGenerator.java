package com.multicloud.authservice.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically-secure URL-safe token.
     * @return 64-character URL-safe base64 token
     */
    public String generateSecureToken() {
        byte[] tokenBytes = new byte[48];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}