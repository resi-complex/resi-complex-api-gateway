package com.resicomplex.gateway.domain.port.out;

import com.resicomplex.gateway.domain.model.GatewayPrincipal;
import java.time.Instant;

public interface JwtVerifierPort {
    GatewayPrincipal verifyBearerToken(String token, Instant now);
}
