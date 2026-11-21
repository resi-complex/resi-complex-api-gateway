package com.resicomplex.gateway.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resicomplex.gateway.domain.exception.GatewaySecurityException;
import com.resicomplex.gateway.infrastructure.security.Hs256GatewayJwtVerifierAdapter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GatewayRoutingApplicationServiceTest {

    private static final String SECRET = "resicomplex-local-dev-hs256-signing-secret-key-at-least-256-bits-long-2026";
    private GatewayRoutingApplicationService routingService;

    @BeforeEach
    void setUp() {
        Hs256GatewayJwtVerifierAdapter verifier = new Hs256GatewayJwtVerifierAdapter(SECRET);
        routingService = new GatewayRoutingApplicationService(
            verifier,
            5,
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:8083",
            "http://localhost:8084",
            "http://localhost:8085",
            "http://localhost:8086",
            "http://localhost:8087",
            "http://localhost:8088",
            "http://localhost:8089"
        );
    }

    @Test
    void allowPublicAuthLoginWithoutToken() {
        var resolved = routingService.resolveAndAuthorize(
            "POST", "/api/v1/auth/login", null, null, "127.0.0.1", Instant.now()
        );
        assertEquals("iam-service", resolved.route().serviceId());
        assertEquals("http://localhost:8081/api/v1/auth/login", resolved.downstreamUri().toString());
        assertTrue(resolved.principal().isEmpty());
    }

    @Test
    void rejectProtectedRouteWhenTokenIsMissing() {
        GatewaySecurityException ex = assertThrows(
            GatewaySecurityException.class,
            () -> routingService.resolveAndAuthorize(
                "GET", "/api/v1/fees", "status=PENDING", null, "127.0.0.2", Instant.now()
            )
        );
        assertEquals(401, ex.getHttpStatus());
        assertEquals("UNAUTHORIZED", ex.getErrorCode());
    }

    @Test
    void verifyValidJwtAndResolveAllDownstreamServices() throws Exception {
        Instant now = Instant.now();
        String token = buildSampleJwt("u-123", "admin@resicomplex.com", "ADMINISTRATOR", now.getEpochSecond() + 3600);

        var billingRoute = routingService.resolveAndAuthorize(
            "GET", "/api/v1/fees/unit/u-1", null, "Bearer " + token, "127.0.0.3", now
        );
        assertEquals("billing-service", billingRoute.route().serviceId());
        assertEquals("http://localhost:8084/api/v1/fees/unit/u-1", billingRoute.downstreamUri().toString());
        assertTrue(billingRoute.principal().isPresent());
        assertEquals("u-123", billingRoute.principal().get().userId());
        assertEquals("ADMINISTRATOR", billingRoute.principal().get().role());
        assertFalse(billingRoute.principal().get().permissions().isEmpty());
    }

    @Test
    void enforcePerClientRateLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            routingService.resolveAndAuthorize("POST", "/auth/login", null, null, "10.0.0.99", now);
        }
        GatewaySecurityException rateEx = assertThrows(
            GatewaySecurityException.class,
            () -> routingService.resolveAndAuthorize("POST", "/auth/login", null, null, "10.0.0.99", now)
        );
        assertEquals(429, rateEx.getHttpStatus());
    }

    private static String buildSampleJwt(String sub, String email, String role, long exp) throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{\"sub\":\"" + sub + "\",\"email\":\"" + email + "\",\"role\":\"" + role
            + "\",\"permissions\":[\"units:*\",\"fees:*\"],\"iat\":" + (exp - 3600) + ",\"exp\":" + exp + ",\"jti\":\"jti-1\"}";
        String payload = enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = enc.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        return input + "." + sig;
    }
}
