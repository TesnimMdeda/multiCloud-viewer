package com.multicloud.cloudprofileservice.repository;

import com.multicloud.cloudprofileservice.entity.OciProfileDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OciProfileDetailsRepository extends JpaRepository<OciProfileDetails, String> {

    Optional<OciProfileDetails> findByProfile_Id(String profileId);

    // convenience alias used by storage adapter
    default Optional<OciProfileDetails> findByProfileId(String profileId) {
        return findByProfile_Id(profileId);
    }

    List<OciProfileDetails> findByProfile_OwnerId(String ownerId);
}