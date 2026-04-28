package com.multicloud.resourcemanagementservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-based in-memory cache for cloud resource discovery results.
 * <p>
 * Cached entries expire after 5 minutes, so repeated requests within that
 * window return instantly instead of re-fetching from cloud provider APIs.
 * Each profile ID gets its own cache entry.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "gcpResources", "ociResources", "resourceStats");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .recordStats());
        return manager;
    }
}
