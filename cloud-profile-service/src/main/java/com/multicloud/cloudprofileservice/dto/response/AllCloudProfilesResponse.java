package com.multicloud.cloudprofileservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AllCloudProfilesResponse {
    private List<CloudProfileResponse> profiles;
    private long totalCount;
    private Map<String, Long> countByProvider;
}
