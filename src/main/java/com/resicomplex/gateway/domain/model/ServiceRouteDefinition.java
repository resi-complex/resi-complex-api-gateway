package com.resicomplex.gateway.domain.model;

import java.util.Objects;

public record ServiceRouteDefinition(
    String serviceId,
    String pathPrefix,
    String targetBaseUrl,
    boolean publicAccess
) {
    public ServiceRouteDefinition {
        Objects.requireNonNull(serviceId, "serviceId must not be null");
        Objects.requireNonNull(pathPrefix, "pathPrefix must not be null");
        Objects.requireNonNull(targetBaseUrl, "targetBaseUrl must not be null");
    }
}
