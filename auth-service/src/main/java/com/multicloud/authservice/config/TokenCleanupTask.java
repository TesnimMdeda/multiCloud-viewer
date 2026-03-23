package com.multicloud.authservice.config;

import com.multicloud.authservice.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled task that removes expired or used password reset tokens.
 * Runs every hour to keep the database clean.
 *
 * Requires @EnableScheduling on AuthServiceApplication.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupTask {

    private final PasswordResetTokenRepository tokenRepository;

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void cleanupExpiredTokens() {
        int deletedExpired = tokenRepository
                .deleteByExpiresAtBefore(LocalDateTime.now());
        int deletedUsed = tokenRepository
                .deleteByUsedTrue();
        log.info("Token cleanup: removed {} expired, {} used tokens",
                deletedExpired, deletedUsed);
    }
}