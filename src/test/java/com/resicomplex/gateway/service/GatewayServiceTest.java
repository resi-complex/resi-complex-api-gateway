package com.resicomplex.gateway.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayServiceTest {

    @Test
    void resolvesCanonicalRoutesCorrectly() {
        GatewayService service = new GatewayService(
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

        assertEquals("http://localhost:8081/api/v1/users", service.resolveTargetUrl("/api/v1/users", null).orElseThrow());
        assertEquals("http://localhost:8083/api/v1/units?page=1", service.resolveTargetUrl("/api/v1/units", "page=1").orElseThrow());
        assertEquals("http://localhost:8085/api/v1/people", service.resolveTargetUrl("/api/v1/people", null).orElseThrow());
        assertTrue(service.resolveTargetUrl("/api/v1/unknown", null).isEmpty());
    }
}
