package com.multicloud.cloudprofileservice.repository;

import com.multicloud.cloudprofileservice.entity.OciProfileDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OciProfileDetailsRepository extends JpaRepository<OciProfileDetails, UUID> {

    List<OciProfileDetails> findByCreatedByUserId(String userId);

    List<OciProfileDetails> findByTenancyOcidAndCreatedByUserId(
            String tenancyOcid, String userId);
}