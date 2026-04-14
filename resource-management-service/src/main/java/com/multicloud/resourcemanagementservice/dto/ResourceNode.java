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

    /**
     * Number of real (non-placeholder) direct children.
     * Placeholder nodes have {@code id == null} and are excluded from the count.
     * Set automatically by {@link #computeCounts()}.
     */
    private Integer count;

    @Builder.Default
    private List<ResourceNode> children = new ArrayList<>();

    public void addChild(ResourceNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }

    /**
     * Creates a placeholder node for a missing level in the hierarchy.
     * The placeholder has null id/name/children so the frontend knows data is
     * absent.
     *
     * @param type the expected child type at this level (e.g. "VCN", "SUBNET",
     *             "RESOURCE")
     */
    public static ResourceNode placeholder(String type) {
        return ResourceNode.builder()
                .id(null)
                .name(null)
                .type(type)
                .children(null) // explicitly null — not an empty array
                .build();
    }

    /**
     * Post-processes the tree to guarantee a fixed-depth hierarchy.
     * Rules applied recursively:
     * <ul>
     * <li>If the current node's type has a mapped expected child type AND the node
     * has no real children, one placeholder child of that type is injected.</li>
     * <li>If the current node's type is NOT in the map (i.e. it is a leaf), its
     * children list is set to {@code null} so the JSON emits
     * {@code "children":null}
     * rather than an empty array.</li>
     * </ul>
     *
     * @param expectedChildType maps each parent node type to the expected child
     *                          placeholder type
     */
    public void ensureHierarchy(Map<String, String> expectedChildType) {
        String childType = expectedChildType.get(this.type);

        if (childType == null) {
            // Leaf node — children must be null, never an empty array
            this.children = null;
            return;
        }

        if (this.children == null || this.children.isEmpty()) {
            // No real children — inject a single placeholder and recurse into it
            ResourceNode ph = ResourceNode.placeholder(childType);
            this.children = new ArrayList<>();
            this.children.add(ph);
            ph.ensureHierarchy(expectedChildType);
        } else {
            // Recurse into every real child
            for (ResourceNode child : this.children) {
                child.ensureHierarchy(expectedChildType);
            }
        }
    }

    /**
     * Recursively computes and sets the {@code count} field on every node.
     * <ul>
     * <li>Placeholder children (id == null) are NOT counted.</li>
     * <li>Leaf nodes (children == null) get count 0.</li>
     * </ul>
     * Call this AFTER {@link #ensureHierarchy(Map)} so placeholder nodes are
     * already in place and the count correctly reflects only real data.
     */
    public void computeCounts() {
        if (this.children == null || this.children.isEmpty()) {
            this.count = 0;
            return;
        }
        // Count only real (non-placeholder) children — placeholders have id == null
        this.count = (int) this.children.stream()
                .filter(c -> c.getId() != null)
                .count();
        // Recurse into all children (real and placeholder alike)
        for (ResourceNode child : this.children) {
            child.computeCounts();
        }
    }
}
