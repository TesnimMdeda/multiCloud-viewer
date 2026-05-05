package com.multicloud.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationPreference {

    @Id
    private String userEmail;

    @Builder.Default
    @Column(nullable = false)
    private boolean notificationsEnabled = true;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
