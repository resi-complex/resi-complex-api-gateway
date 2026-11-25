package com.resicomplex.gateway.service;

import com.resicomplex.gateway.model.ServiceRoute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Service
public class GatewayService {

    private final List<ServiceRoute> routes;
    private final RestClient restClient;

    public GatewayService(
            @Value("${gateway.routes.iam:http://localhost:8081}") String iamUrl,
            @Value("${gateway.routes.communications:http://localhost:8082}") String communicationsUrl,
            @Value("${gateway.routes.units:http://localhost:8083}") String unitsUrl,
            @Value("${gateway.routes.billing:http://localhost:8084}") String billingUrl,
            @Value("${gateway.routes.people:http://localhost:8085}") String peopleUrl,
            @Value("${gateway.routes.maintenance:http://localhost:8086}") String maintenanceUrl,
            @Value("${gateway.routes.access-control:http://localhost:8087}") String accessControlUrl,
            @Value("${gateway.routes.finance-approval:http://localhost:8088}") String financeUrl,
            @Value("${gateway.routes.reports:http://localhost:8089}") String reportsUrl
    ) {
        this.routes = List.of(
                new ServiceRoute("/api/v1/auth", iamUrl),
                new ServiceRoute("/api/v1/users", iamUrl),
                new ServiceRoute("/api/v1/roles", iamUrl),
                new ServiceRoute("/api/v1/communications", communicationsUrl),
                new ServiceRoute("/api/v1/units", unitsUrl),
                new ServiceRoute("/api/v1/billing", billingUrl),
                new ServiceRoute("/api/v1/people", peopleUrl),
                new ServiceRoute("/api/v1/maintenance", maintenanceUrl),
                new ServiceRoute("/api/v1/access-control", accessControlUrl),
                new ServiceRoute("/api/v1/finance", financeUrl),
                new ServiceRoute("/api/v1/reports", reportsUrl)
        );
        this.restClient = RestClient.create();
    }

    public List<ServiceRoute> getRoutes() {
        return routes;
    }

    public Optional<String> resolveTargetUrl(String path, String query) {
        return routes.stream()
                .filter(route -> path.startsWith(route.prefix()))
                .findFirst()
                .map(route -> route.targetBaseUrl() + path + (query != null && !query.isBlank() ? "?" + query : ""));
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, String targetUrl, HttpHeaders headers, byte[] body) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(targetUrl);
        if (headers.getContentType() != null) {
            spec.contentType(headers.getContentType());
        }
        if (body != null && body.length > 0) {
            spec.body(body);
        }
        return spec.retrieve().toEntity(byte[].class);
    }
}
