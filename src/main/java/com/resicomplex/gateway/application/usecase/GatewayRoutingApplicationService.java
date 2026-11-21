package com.resicomplex.gateway.application.usecase;

import com.resicomplex.gateway.domain.exception.GatewaySecurityException;
import com.resicomplex.gateway.domain.model.GatewayPrincipal;
import com.resicomplex.gateway.domain.model.ServiceRouteDefinition;
import com.resicomplex.gateway.domain.port.in.GatewayRoutingUseCasePort;
import com.resicomplex.gateway.domain.port.out.JwtVerifierPort;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GatewayRoutingApplicationService implements GatewayRoutingUseCasePort {

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/health",
        "/health/ready",
        "/login",
        "/register",
        "/refresh",
        "/activate",
        "/auth/login",
        "/auth/register",
        "/auth/refresh",
        "/auth/activate",
        "/api/v1/auth/health",
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/auth/activate"
    );

    private final JwtVerifierPort jwtVerifier;
    private final List<ServiceRouteDefinition> routeTable;
    private final int requestsPerMinuteLimit;
    private final Map<String, RateBucket> clientBuckets = new ConcurrentHashMap<>();

    private record RateBucket(long minuteEpoch, AtomicInteger counter) {}

    public GatewayRoutingApplicationService(
        JwtVerifierPort jwtVerifier,
        @Value("${gateway.rate-limit.requests-per-minute:240}") int requestsPerMinuteLimit,
        @Value("${gateway.routes.iam:http://localhost:8081}") String iamUrl,
        @Value("${gateway.routes.communications:http://localhost:8082}") String communicationsUrl,
        @Value("${gateway.routes.people:http://localhost:8083}") String peopleUrl,
        @Value("${gateway.routes.billing:http://localhost:8084}") String billingUrl,
        @Value("${gateway.routes.units:http://localhost:8085}") String unitsUrl,
        @Value("${gateway.routes.maintenance:http://localhost:8086}") String maintenanceUrl,
        @Value("${gateway.routes.access-control:http://localhost:8087}") String accessControlUrl,
        @Value("${gateway.routes.finance-approval:http://localhost:8088}") String financeApprovalUrl,
        @Value("${gateway.routes.reports:http://localhost:8089}") String reportsUrl
    ) {
        this.jwtVerifier = jwtVerifier;
        this.requestsPerMinuteLimit = requestsPerMinuteLimit;
        this.routeTable = List.of(
            new ServiceRouteDefinition("iam-service", "/api/v1/auth", iamUrl, true),
            new ServiceRouteDefinition("iam-service", "/auth", iamUrl, true),
            new ServiceRouteDefinition("iam-service", "/api/v1/users", iamUrl, false),
            new ServiceRouteDefinition("iam-service", "/users", iamUrl, false),
            new ServiceRouteDefinition("iam-service", "/api/v1/roles", iamUrl, false),
            new ServiceRouteDefinition("iam-service", "/roles", iamUrl, false),
            new ServiceRouteDefinition("units-service", "/api/v1/units", unitsUrl, false),
            new ServiceRouteDefinition("people-service", "/api/v1/people", peopleUrl, false),
            new ServiceRouteDefinition("people-service", "/api/v1/persons", peopleUrl, false),
            new ServiceRouteDefinition("maintenance-service", "/api/v1/maintenance", maintenanceUrl, false),
            new ServiceRouteDefinition("maintenance-service", "/api/v1/requests", maintenanceUrl, false),
            new ServiceRouteDefinition("billing-service", "/api/v1/fees", billingUrl, false),
            new ServiceRouteDefinition("billing-service", "/api/v1/billing", billingUrl, false),
            new ServiceRouteDefinition("communications-service", "/api/v1/announcements", communicationsUrl, false),
            new ServiceRouteDefinition("communications-service", "/api/v1/communications", communicationsUrl, false),
            new ServiceRouteDefinition("access-control-service", "/api/v1/visits", accessControlUrl, false),
            new ServiceRouteDefinition("access-control-service", "/api/v1/correspondence", accessControlUrl, false),
            new ServiceRouteDefinition("access-control-service", "/api/v1/access", accessControlUrl, false),
            new ServiceRouteDefinition("finance-approval-service", "/api/v1/expense-proposals", financeApprovalUrl, false),
            new ServiceRouteDefinition("finance-approval-service", "/api/v1/finance", financeApprovalUrl, false),
            new ServiceRouteDefinition("reports-service", "/api/v1/reports", reportsUrl, false)
        );
    }

    @Override
    public boolean isPublicEndpoint(String method, String path) {
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        return PUBLIC_PATHS.contains(path)
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs");
    }

    @Override
    public ResolvedRoute resolveAndAuthorize(
        String method,
        String requestPath,
        String rawQuery,
        String authorizationHeader,
        String clientIp,
        Instant now
    ) {
        enforceRateLimit(clientIp != null ? clientIp : "anonymous", now);

        ServiceRouteDefinition matchedRoute = routeTable.stream()
            .filter(r -> requestPath.equals(r.pathPrefix()) || requestPath.startsWith(r.pathPrefix() + "/"))
            .findFirst()
            .orElseThrow(() -> new GatewaySecurityException(
                "ROUTE_NOT_FOUND",
                "No downstream microservice route configured for path: " + requestPath,
                404
            ));

        Optional<GatewayPrincipal> principal = Optional.empty();
        if (!isPublicEndpoint(method, requestPath)) {
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                throw new GatewaySecurityException(
                    "UNAUTHORIZED",
                    "Missing or invalid Authorization Bearer header",
                    401
                );
            }
            String rawToken = authorizationHeader.substring(7).trim();
            principal = Optional.of(jwtVerifier.verifyBearerToken(rawToken, now));
        } else if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String rawToken = authorizationHeader.substring(7).trim();
            principal = Optional.of(jwtVerifier.verifyBearerToken(rawToken, now));
        }

        String fullTarget = matchedRoute.targetBaseUrl() + requestPath
            + (rawQuery != null && !rawQuery.isBlank() ? "?" + rawQuery : "");

        return new ResolvedRoute(matchedRoute, URI.create(fullTarget), principal);
    }

    @Override
    public List<ServiceRouteDefinition> listRoutes() {
        return routeTable;
    }

    private void enforceRateLimit(String clientKey, Instant now) {
        long currentMinute = now.getEpochSecond() / 60;
        RateBucket bucket = clientBuckets.compute(clientKey, (k, existing) -> {
            if (existing == null || existing.minuteEpoch() != currentMinute) {
                return new RateBucket(currentMinute, new AtomicInteger(1));
            }
            existing.counter().incrementAndGet();
            return existing;
        });
        if (bucket.counter().get() > requestsPerMinuteLimit) {
            throw new GatewaySecurityException(
                "RATE_LIMIT_EXCEEDED",
                "Too many requests; limit is " + requestsPerMinuteLimit + " requests per minute",
                429
            );
        }
    }
}
