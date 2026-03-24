package com.multicloud.cloudprofileservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gcp_profile_details")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class GcpProfileDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private CloudProfile profile;

    @Column(nullable = false)
    private String projectId; // e.g. "my-project-123"

    // ─── Fields auto-extracted from the Service Account JSON key ───

    @Column(nullable = false)
    private String serviceAccountEmail; // from key: "client_email"

    @Column(nullable = false)
    private String clientId; // from key: "client_id"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedServiceAccountKey; // AES-256 encrypted JSON

    @Column(nullable = false)
    private String keyType; // "service_account"

    @Column
    private String tokenUri; // from key: "token_uri"
}