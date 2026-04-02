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
    private String projectId;


    @Column(nullable = false)
    private String serviceAccountEmail;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedServiceAccountKey;

    @Column(nullable = false)
    private String keyType;

    @Column
    private String tokenUri;
}