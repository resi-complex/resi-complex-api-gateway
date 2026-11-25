package com.resicomplex.gateway.controller;

import com.resicomplex.gateway.model.ServiceRoute;
import com.resicomplex.gateway.service.GatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class GatewayController {

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "api-gateway");
    }

    @GetMapping("/api/v1/routes")
    public List<ServiceRoute> listRoutes() {
        return gatewayService.getRoutes();
    }

    @RequestMapping("/api/v1/**")
    public ResponseEntity<?> proxy(
            HttpMethod method,
            HttpServletRequest request,
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) byte[] body
    ) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        return gatewayService.resolveTargetUrl(path, query)
                .<ResponseEntity<?>>map(url -> gatewayService.forward(method, url, headers, body))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Ruta no encontrada en el API Gateway: " + path)));
    }
}
