package com.resicomplex.gateway.domain.port.in;

import com.resicomplex.gateway.domain.model.GatewayPrincipal;
import com.resicomplex.gateway.domain.model.ServiceRouteDefinition;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GatewayRoutingUseCasePort {

    record ResolvedRoute(
        ServiceRouteDefinition route,
        URI downstreamUri,
        Optional<GatewayPrincipal> principal
    ) {}

    boolean isPublicEndpoint(String method, String path);

    ResolvedRoute resolveAndAuthorize(
        String method,
        String requestPath,
        String rawQuery,
        String authorizationHeader,
        String clientIp,
        Instant now
    );

    List<ServiceRouteDefinition> listRoutes();
}
