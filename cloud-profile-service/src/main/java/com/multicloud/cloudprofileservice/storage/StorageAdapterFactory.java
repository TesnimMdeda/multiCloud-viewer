package com.multicloud.cloudprofileservice.storage;

import com.multicloud.cloudprofileservice.entity.CloudProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Automatically collects all CloudStorageAdapter beans by provider.
 * Adding a new provider = add a new @Component adapter. Zero changes here.
 */
@Component
public class StorageAdapterFactory {

    private final Map<CloudProvider, CloudStorageAdapter> adapters;

    public StorageAdapterFactory(List<CloudStorageAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(
                        CloudStorageAdapter::getSupportedProvider,
                        Function.identity()
                ));
    }

    public CloudStorageAdapter getAdapter(CloudProvider provider) {
        var adapter = adapters.get(provider);
        if (adapter == null) {
            throw new UnsupportedOperationException(
                    "No storage adapter found for provider: " + provider);
        }
        return adapter;
    }
}