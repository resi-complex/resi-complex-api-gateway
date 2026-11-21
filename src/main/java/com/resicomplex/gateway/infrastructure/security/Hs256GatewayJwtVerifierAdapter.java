package com.resicomplex.gateway.infrastructure.security;

import com.resicomplex.gateway.domain.exception.GatewaySecurityException;
import com.resicomplex.gateway.domain.model.GatewayPrincipal;
import com.resicomplex.gateway.domain.port.out.JwtVerifierPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Hs256GatewayJwtVerifierAdapter implements JwtVerifierPort {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secretBytes;

    public Hs256GatewayJwtVerifierAdapter(
        @Value("${gateway.jwt.secret:resicomplex-local-dev-hs256-signing-secret-key-at-least-256-bits-long-2026}") String secret
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Gateway JWT secret must be at least 32 bytes");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public GatewayPrincipal verifyBearerToken(String token, Instant now) {
        if (token == null || token.isBlank()) {
            throw new GatewaySecurityException("MISSING_TOKEN", "Authorization Bearer token is required", 401);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new GatewaySecurityException("MALFORMED_TOKEN", "Invalid JWT token format", 401);
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = computeHmac(signingInput);
        if (!MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.UTF_8),
            parts[2].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new GatewaySecurityException("INVALID_SIGNATURE", "JWT signature verification failed", 401);
        }

        String payloadJson;
        try {
            payloadJson = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new GatewaySecurityException("MALFORMED_TOKEN", "Invalid Base64url JWT payload", 401);
        }

        long exp = extractLong(payloadJson, "exp");
        if (now.getEpochSecond() >= exp) {
            throw new GatewaySecurityException("TOKEN_EXPIRED", "Access token has expired", 401);
        }

        String sub = extractString(payloadJson, "sub");
        String email = extractString(payloadJson, "email");
        String role = extractString(payloadJson, "role");
        String jti = extractString(payloadJson, "jti");
        List<String> permissions = extractStringArray(payloadJson, "permissions");

        return new GatewayPrincipal(sub, email, role, permissions, jti, Instant.ofEpochSecond(exp));
    }

    private String computeHmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGO));
            return URL_ENCODER.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC computation failure", ex);
        }
    }

    private static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!m.find()) {
            throw new GatewaySecurityException("INVALID_CLAIMS", "Missing JWT claim: " + key, 401);
        }
        return m.group(1);
    }

    private static long extractLong(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find()) {
            throw new GatewaySecurityException("INVALID_CLAIMS", "Missing numeric JWT claim: " + key, 401);
        }
        return Long.parseLong(m.group(1));
    }

    private static List<String> extractStringArray(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (!m.find()) {
            return List.of();
        }
        String rawInside = m.group(1);
        List<String> out = new ArrayList<>();
        Matcher itemMatcher = Pattern.compile("\"([^\"]+)\"").matcher(rawInside);
        while (itemMatcher.find()) {
            out.add(itemMatcher.group(1));
        }
        return out;
    }
}
