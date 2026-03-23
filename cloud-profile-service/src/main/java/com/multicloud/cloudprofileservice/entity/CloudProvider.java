package com.multicloud.cloudprofileservice.entity;

/**
 * Extensible provider enum.
 * Add new providers here — the Strategy pattern auto-picks the right validator.
 */
public enum CloudProvider {
    GCP,
    OCI,
}
