package com.multicloud.notificationservice.service;

import com.multicloud.notificationservice.entity.Notification;
import com.multicloud.notificationservice.entity.NotificationPreference;
import com.multicloud.notificationservice.exception.NotificationsDisabledException;
import com.multicloud.notificationservice.repository.NotificationPreferenceRepository;
import com.multicloud.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    // One active SSE emitter per user (latest connection wins)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // SSE Connection
    // ─────────────────────────────────────────────────────────────────────────

    public SseEmitter createEmitter(String userEmail) {
        // Close any existing emitter for this user (e.g., user opened a new tab)
        SseEmitter existing = emitters.remove(userEmail);
        if (existing != null) {
            try { existing.complete(); } catch (Exception ignored) {}
        }

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(userEmail, emitter);

        emitter.onCompletion(() -> emitters.remove(userEmail));
        emitter.onTimeout(() -> {
            emitters.remove(userEmail);
            log.debug("SSE timeout for user {}", userEmail);
        });
        emitter.onError(e -> {
            emitters.remove(userEmail);
            log.debug("SSE error for user {}: {}", userEmail, e.getMessage());
        });

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connection established"));
        } catch (IOException e) {
            log.error("Error sending INIT event to user {}", userEmail, e);
        }

        return emitter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send Notification  (checks preference FIRST)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Notification sendNotification(Notification notification) {
        // Guard: respect user's preference
        boolean enabled = preferenceRepository.findById(notification.getUserEmail())
                .map(NotificationPreference::isNotificationsEnabled)
                .orElse(true); // default = enabled when no record exists

        if (!enabled) {
            log.info("Notification blocked – user '{}' has notifications disabled.", notification.getUserEmail());
            throw new NotificationsDisabledException(
                    "Notifications are disabled for user: " + notification.getUserEmail());
        }

        Notification saved = notificationRepository.save(notification);
        pushSseEvent(saved);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(String userEmail) {
        return notificationRepository.findAllByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userEmail) {
        return notificationRepository.countByUserEmailAndReadFalse(userEmail);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Write Operations  (optimized: single UPDATE query each)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void markAsRead(String id) {
        // One UPDATE query – no SELECT + re-save overhead
        notificationRepository.markAsReadById(id);
    }

    @Transactional
    public void markAllAsRead(String userEmail) {
        // One bulk UPDATE – no in-memory filtering + saveAll loop
        notificationRepository.markAllAsReadByUserEmail(userEmail);
    }

    @Transactional
    public void deleteNotification(String id) {
        notificationRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification Preferences
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isNotificationsEnabled(String userEmail) {
        return preferenceRepository.findById(userEmail)
                .map(NotificationPreference::isNotificationsEnabled)
                .orElse(true);
    }

    @Transactional
    public void setNotificationsEnabled(String userEmail, boolean enabled) {
        NotificationPreference pref = preferenceRepository.findById(userEmail)
                .orElseGet(() -> NotificationPreference.builder().userEmail(userEmail).build());
        pref.setNotificationsEnabled(enabled);
        preferenceRepository.save(pref);
        log.info("Notifications {} for user '{}'", enabled ? "ENABLED" : "DISABLED", userEmail);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void pushSseEvent(Notification notification) {
        SseEmitter emitter = emitters.get(notification.getUserEmail());
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("NOTIFICATION")
                    .data(notification)
                    .id(notification.getId()));
        } catch (IOException e) {
            log.error("Failed to push SSE event to user '{}', removing emitter.", notification.getUserEmail(), e);
            emitters.remove(notification.getUserEmail());
        }
    }
}
