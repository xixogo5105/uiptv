package com.uiptv.shared.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record BackgroundJobRequest(String jobName,
                                   Map<String, String> parameters,
                                   boolean requiresNetwork,
                                   BackgroundJobUniqueness uniqueness) {
    public BackgroundJobRequest {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("jobName is required");
        }
        parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        uniqueness = uniqueness == null ? BackgroundJobUniqueness.KEEP_EXISTING : uniqueness;
    }
}
