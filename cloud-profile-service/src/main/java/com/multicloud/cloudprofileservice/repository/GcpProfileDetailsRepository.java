package com.multicloud.cloudprofileservice.repository;

import com.multicloud.cloudprofileservice.entity.GcpProfileDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GcpProfileDetailsRepository extends JpaRepository<GcpProfileDetails, String> {

    Optional<GcpProfileDetails> findByProfile_Id(String profileId);

    // convenience alias used by storage adapter
    default Optional<GcpProfileDetails> findByProfileId(String profileId) {
        return findByProfile_Id(profileId);
    }

    List<GcpProfileDetails> findByProfile_OwnerId(String ownerId);
}