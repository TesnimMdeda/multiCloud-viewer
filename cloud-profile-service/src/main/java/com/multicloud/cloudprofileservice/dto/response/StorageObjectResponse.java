package com.multicloud.cloudprofileservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class StorageObjectResponse {

    private String name;
    private long size;
    private String contentType;
    private Instant lastModified;
}