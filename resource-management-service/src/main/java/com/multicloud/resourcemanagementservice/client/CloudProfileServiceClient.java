package com.multicloud.resourcemanagementservice.client;

import com.multicloud.resourcemanagementservice.client.dto.ApiResponse;
import com.multicloud.resourcemanagementservice.client.dto.GcpProfileDetailsResponse;
import com.multicloud.resourcemanagementservice.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "cloud-profile-service", url = "${app.services.cloud-profile-url}", configuration = FeignClientConfig.class)
public interface CloudProfileServiceClient {

    @GetMapping("/api/cloud/gcp/internal/{profileId}/details")
    ApiResponse<GcpProfileDetailsResponse> getGcpProfileDetails(@PathVariable("profileId") String profileId);

    @GetMapping("/api/cloud/oci/internal/{profileId}/details")
    ApiResponse<com.multicloud.resourcemanagementservice.client.dto.OciProfileDetailsResponse> getOciProfileDetails(@PathVariable("profileId") String profileId);

    @GetMapping("/api/cloud/internal/{profileId}")
    ApiResponse<com.multicloud.resourcemanagementservice.client.dto.CloudProfileResponse> getProfile(@PathVariable("profileId") String profileId);
}
