package com.multicloud.authservice.service;

import com.multicloud.authservice.entity.User;

public interface EmailService {
    void sendPasswordSetupEmail(User user, String setupLink);
    void sendPasswordResetEmail(User user, String resetLink);
}