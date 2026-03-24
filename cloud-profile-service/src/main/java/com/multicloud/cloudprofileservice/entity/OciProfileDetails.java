package com.multicloud.cloudprofileservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "oci_profile_details")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OciProfileDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private CloudProfile profile;

    @Column(nullable = false)
    private String tenancyOcid;

    @Column(nullable = false)
    private String userOcid;

    @Column(nullable = false)
    private String fingerprint;

    // ─── Fields auto-extracted after OCI validation ───

    @Column
    private String tenancyName; // from Identity SDK

    @Column
    private String homeRegion;  // from Identity SDK

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey; // AES-256 encrypted .pem

    @Column
    private String compartmentId; // root compartment = tenancyOcid
}

