package com.multicloud.notificationservice.controller;

import com.multicloud.notificationservice.entity.Notification;
import com.multicloud.notificationservice.exception.NotificationsDisabledException;
import com.multicloud.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // ─── SSE stream ───────────────────────────────────────────────────────────

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null; // Spring Security handles the 401, this is just for safety
        }
        return notificationService.createEmitter(authentication.getName());
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    @GetMapping
    public List<Notification> getNotifications(Authentication authentication) {
        return notificationService.getNotifications(authentication.getName());
    }

    @GetMapping("/unread-count")
    public long getUnreadCount(Authentication authentication) {
        return notificationService.getUnreadCount(authentication.getName());
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable String id) {
        notificationService.markAsRead(id);
    }

    @PutMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNotification(@PathVariable String id) {
        notificationService.deleteNotification(id);
    }

    // ─── Internal send (called by other microservices) ────────────────────────

    @PostMapping("/send")
    public ResponseEntity<?> sendNotification(@RequestBody Notification notification) {
        Notification saved = notificationService.sendNotification(notification);
        return ResponseEntity.ok(saved);
    }

    // ─── Preferences ─────────────────────────────────────────────────────────

    @GetMapping("/preferences")
    public Map<String, Boolean> getPreferences(Authentication authentication) {
        boolean enabled = notificationService.isNotificationsEnabled(authentication.getName());
        return Map.of("notificationsEnabled", enabled);
    }

    @PutMapping("/preferences")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePreferences(
            Authentication authentication,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("notificationsEnabled"));
        notificationService.setNotificationsEnabled(authentication.getName(), enabled);
    }

    // ─── Exception handling ───────────────────────────────────────────────────

    @ExceptionHandler(NotificationsDisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleNotificationsDisabled(NotificationsDisabledException ex) {
        return Map.of("error", ex.getMessage());
    }
}
