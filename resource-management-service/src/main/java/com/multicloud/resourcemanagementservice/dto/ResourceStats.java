package com.multicloud.resourcemanagementservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceStats {
    private String provider;
    private int projectCount;
    private int vpcCount;
    private int subnetCount;
    private int computeCount;
    private int storageCount;
    private int diskCount;
}
