package com.multicloud.cloudprofileservice.repository;

import com.multicloud.cloudprofileservice.entity.CloudProfile;
import com.multicloud.cloudprofileservice.entity.CloudProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CloudProfileRepository extends JpaRepository<CloudProfile, String> {

    List<CloudProfile> findByOwnerId(String ownerId);

    List<CloudProfile> findByOwnerIdAndProvider(String ownerId, CloudProvider provider);

    boolean existsByProfileNameAndOwnerId(String profileName, String ownerId);
}