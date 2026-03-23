package com.multicloud.cloudprofileservice.config;

import com.multicloud.cloudprofileservice.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptionConfig {

    @Value("${encryption.secret-key}")
    private String encryptionKey;

    @Bean
    public EncryptionUtil encryptionUtil() {
        return new EncryptionUtil(encryptionKey);
    }
}