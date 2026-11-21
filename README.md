# resi-complex-api-gateway

Central API Gateway for **resi-complex** (`ADR-006`, resolves `AT-001`), built with **Hexagonal Architecture**, **Java 25**, and **Spring Boot 4.0.8**.

## Key Capabilities (`ADR-006`)
1. **Single Entry Point & Reverse Proxy Routing:** Routes incoming requests across all 9 Bounded Context microservices (`iam-service`, `communications-service`, `people-service`, `billing-service`, `units-service`, `maintenance-service`, `access-control-service`, `finance-approval-service`, `reports-service`).
2. **Centralized HS256 JWT Verification:** Validates signature and expiration (`exp`) on all protected routes.
3. **RBAC Context Propagation:** Injects trusted downstream HTTP headers (`X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-User-Permissions`, `X-Correlation-ID`).
4. **Centralized CORS & Rate Limiting:** Per-client IP rate limiting (`429 Too Many Requests`) and CORS policy.

## Port Configuration
- **Default Local Port:** `8090` (avoids local port `:8080` conflict; override via `SERVER_PORT=8080` in Docker Compose).
- **Health Check:** `GET /health` and `GET /health/ready`
- **Route Table Inspection:** `GET /gateway/routes`

## Downstream Route Map
| Service | Default URL | Routed Path Prefixes |
|---|---|---|
| `iam-service` | `http://localhost:8081` | `/api/v1/auth/**`, `/auth/**`, `/api/v1/users/**`, `/users/**`, `/api/v1/roles/**`, `/roles` |
| `communications-service` | `http://localhost:8082` | `/api/v1/announcements/**`, `/api/v1/communications/**` |
| `people-service` | `http://localhost:8083` | `/api/v1/people/**`, `/api/v1/persons/**` |
| `billing-service` | `http://localhost:8084` | `/api/v1/fees/**`, `/api/v1/billing/**` |
| `units-service` | `http://localhost:8085` | `/api/v1/units/**` |
| `maintenance-service` | `http://localhost:8086` | `/api/v1/maintenance/**`, `/api/v1/requests/**` |
| `access-control-service` | `http://localhost:8087` | `/api/v1/visits/**`, `/api/v1/correspondence/**`, `/api/v1/access/**` |
| `finance-approval-service` | `http://localhost:8088` | `/api/v1/expense-proposals/**`, `/api/v1/finance/**` |
| `reports-service` | `http://localhost:8089` | `/api/v1/reports/**` |
