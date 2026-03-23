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

    @Column
    private String tenancyName;

    @Column
    private String homeRegion;

    @Column(nullable = false)
    @Lob
    private String encryptedPrivateKey;

    @Column
    private String compartmentId;
}