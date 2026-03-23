package com.multicloud.authservice.repository;

import com.multicloud.authservice.entity.PasswordResetToken;
import com.multicloud.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUser(User user);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.user = :user AND t.used = false AND t.expiresAt > :now")
    Optional<PasswordResetToken> findValidTokenByUser(
            @Param("user") User user,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.used = true")
    int deleteByUsedTrue();
}
