package com.resicomplex.gateway.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record GatewayPrincipal(
    String userId,
    String email,
    String role,
    List<String> permissions,
    String jti,
    Instant expiresAt
) {
    public GatewayPrincipal {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        permissions = permissions != null ? List.copyOf(permissions) : List.of();
    }

    public String permissionsHeaderValue() {
        return String.join(",", permissions);
    }
}
