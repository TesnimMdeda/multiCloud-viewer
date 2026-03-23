package com.multicloud.cloudprofileservice.repository;

import com.multicloud.cloudprofileservice.entity.CloudProfile;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import com.multicloud.cloudprofileservice.entity.ProfileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CloudProfileRepository extends JpaRepository<CloudProfile, UUID> {

    List<CloudProfile> findByCreatedByUserId(String userId);

    List<CloudProfile> findByCreatedByUserIdAndProvider(String userId, CloudProvider provider);

    List<CloudProfile> findByStatus(ProfileStatus status);

    boolean existsByProfileNameAndCreatedByUserId(String profileName, String userId);
}