package com.resicomplex.gateway.infrastructure.web;

import com.resicomplex.gateway.domain.exception.GatewaySecurityException;
import com.resicomplex.gateway.domain.model.GatewayPrincipal;
import com.resicomplex.gateway.domain.model.ServiceRouteDefinition;
import com.resicomplex.gateway.domain.port.in.GatewayRoutingUseCasePort;
import com.resicomplex.gateway.domain.port.in.GatewayRoutingUseCasePort.ResolvedRoute;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RestControllerAdvice
public class GatewayProxyEndpoint {

    private final GatewayRoutingUseCasePort routingUseCase;
    private final HttpClient httpClient;

    public GatewayProxyEndpoint(GatewayRoutingUseCasePort routingUseCase) {
        this.routingUseCase = routingUseCase;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @GetMapping({"/health", "/health/ready"})
    public Map<String, Object> gatewayHealth() {
        return Map.of(
            "status", "ok",
            "service", "api-gateway",
            "configuredRoutes", routingUseCase.listRoutes().size(),
            "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/gateway/routes")
    public List<ServiceRouteDefinition> inspectRoutes() {
        return routingUseCase.listRoutes();
    }

    @RequestMapping({
        "/api/v1/**",
        "/auth/**",
        "/users",
        "/users/**",
        "/roles"
    })
    public ResponseEntity<byte[]> forwardToDownstream(
        HttpServletRequest request,
        @RequestBody(required = false) byte[] requestBody
    ) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String authHeader = request.getHeader("Authorization");
        String clientIp = request.getRemoteAddr();
        String correlationId = request.getHeader("X-Correlation-ID") != null
            ? request.getHeader("X-Correlation-ID")
            : UUID.randomUUID().toString();

        ResolvedRoute resolved = routingUseCase.resolveAndAuthorize(
            method, path, query, authHeader, clientIp, Instant.now()
        );

        HttpRequest.BodyPublisher bodyPublisher = (requestBody != null && requestBody.length > 0)
            ? HttpRequest.BodyPublishers.ofByteArray(requestBody)
            : HttpRequest.BodyPublishers.noBody();

        HttpRequest.Builder proxyReqBuilder = HttpRequest.newBuilder()
            .uri(resolved.downstreamUri())
            .timeout(Duration.ofSeconds(15))
            .method(method, bodyPublisher)
            .header("X-Correlation-ID", correlationId);

        if (request.getContentType() != null) {
            proxyReqBuilder.header("Content-Type", request.getContentType());
        }
        if (authHeader != null) {
            proxyReqBuilder.header("Authorization", authHeader);
        }

        // Context Propagation Headers per ADR-006
        if (resolved.principal().isPresent()) {
            GatewayPrincipal p = resolved.principal().get();
            proxyReqBuilder.header("X-User-Id", p.userId());
            proxyReqBuilder.header("X-User-Email", p.email());
            proxyReqBuilder.header("X-User-Roles", p.role());
            proxyReqBuilder.header("X-User-Permissions", p.permissionsHeaderValue());
        }

        try {
            HttpResponse<byte[]> upstreamResp = httpClient.send(
                proxyReqBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
            );

            HttpHeaders outHeaders = new HttpHeaders();
            outHeaders.set("X-Correlation-ID", correlationId);
            outHeaders.set("X-Routed-Service", resolved.route().serviceId());
            upstreamResp.headers().firstValue("Content-Type")
                .ifPresent(ct -> outHeaders.set(HttpHeaders.CONTENT_TYPE, ct));

            return ResponseEntity.status(upstreamResp.statusCode())
                .headers(outHeaders)
                .body(upstreamResp.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new GatewaySecurityException(
                "DOWNSTREAM_UNAVAILABLE",
                "Downstream service '" + resolved.route().serviceId() + "' is unreachable at " + resolved.route().targetBaseUrl(),
                503
            );
        }
    }

    @ExceptionHandler(GatewaySecurityException.class)
    public ResponseEntity<Map<String, Object>> onGatewayException(GatewaySecurityException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        Map<String, Object> problem = Map.of(
            "type", "urn:resicomplex:gateway:" + ex.getErrorCode().toLowerCase(),
            "title", status.getReasonPhrase(),
            "status", status.value(),
            "error", ex.getErrorCode(),
            "message", ex.getMessage(),
            "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(problem);
    }
}
