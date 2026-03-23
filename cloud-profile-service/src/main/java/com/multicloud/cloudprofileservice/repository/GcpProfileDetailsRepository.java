package com.multicloud.cloudprofileservice.repository;

import com.multicloud.cloudprofileservice.entity.GcpProfileDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GcpProfileDetailsRepository extends JpaRepository<GcpProfileDetails, UUID> {

    Optional<GcpProfileDetails> findByProjectIdAndCreatedByUserId(
            String projectId, String userId);

    List<GcpProfileDetails> findByCreatedByUserId(String userId);
}