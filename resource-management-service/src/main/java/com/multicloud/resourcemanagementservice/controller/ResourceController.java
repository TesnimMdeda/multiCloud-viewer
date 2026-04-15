package com.multicloud.resourcemanagementservice.controller;

import com.multicloud.resourcemanagementservice.dto.ResourceNode;
import com.multicloud.resourcemanagementservice.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@Tag(name = "Resources", description = "Monitor and manage cloud resources across providers")
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/gcp")
    @Operation(summary = "Fetch GCP resources (Multi-Project Discovery)",
            description = "Specialized discovery for GCP that scans all projects accessible by the profile credentials.")
    public ResponseEntity<ResourceNode> getGcpResources(
            @Parameter(description = "The ID of the GCP cloud profile", required = true)
            @RequestParam String cloudProfileId) {
        log.info("Performing multi-project GCP discovery for profile: {}", cloudProfileId);
        ResourceNode gcpTree = resourceService.getGcpResources(cloudProfileId);
        return ResponseEntity.ok(gcpTree);
    }

    @GetMapping("/oci")
    @Operation(summary = "Fetch OCI resources (Tenancy/Compartment Level)",
            description = "Specialized discovery for OCI that scans compartments and services (Network, Compute, Storage).")
    public ResponseEntity<ResourceNode> getOciResources(
            @Parameter(description = "The ID of the OCI cloud profile", required = true)
            @RequestParam String cloudProfileId) {
        log.info("Fetching OCI resources for profile: {}", cloudProfileId);
        ResourceNode ociTree = resourceService.getOciResources(cloudProfileId);
        return ResponseEntity.ok(ociTree);
    }

    @GetMapping("/stats")
    @Operation(summary = "Fetch resource statistics",
            description = "Returns aggregated counts of various resource types (Projects, VPCs, Subnets, etc.) for a specific cloud profile.")
    public ResponseEntity<com.multicloud.resourcemanagementservice.dto.ResourceStats> getStats(
            @Parameter(description = "The ID of the Cloud Profile", required = true)
            @RequestParam String cloudProfileId) {
        log.info("Fetching statistics for profile: {}", cloudProfileId);
        return ResponseEntity.ok(resourceService.getStats(cloudProfileId));
    }
}
