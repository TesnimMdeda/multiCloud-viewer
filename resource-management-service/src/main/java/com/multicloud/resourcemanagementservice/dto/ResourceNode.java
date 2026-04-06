package com.multicloud.resourcemanagementservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceNode {
    private String id;
    private String name;
    private String type;
    private Map<String, Object> details;
    
    @Builder.Default
    private List<ResourceNode> children = new ArrayList<>();

    public void addChild(ResourceNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
