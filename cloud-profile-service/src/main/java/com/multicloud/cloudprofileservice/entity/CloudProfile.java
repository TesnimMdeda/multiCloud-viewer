package com.multicloud.cloudprofileservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cloud_profiles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CloudProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String profileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CloudProvider provider;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProfileStatus status = ProfileStatus.PENDING_VALIDATION;

    @Column
    private String validationError;

    @Column
    private LocalDateTime lastValidatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum ProfileStatus {
        PENDING_VALIDATION,
        VALID,
        INVALID
    }
}