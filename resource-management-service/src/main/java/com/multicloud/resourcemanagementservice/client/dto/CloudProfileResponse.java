package com.multicloud.resourcemanagementservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudProfileResponse {
    private String id;
    private String profileName;
    private String provider;
    private String region;
    private String status;
    private String ownerId;
}
