# Library Microservices (Cloud-Native)

![Java](https://img.shields.io/badge/Java-23-007396?logo=openjdk&logoColor=white)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-6DB33F?logo=springboot&logoColor=white)
![Spring%20Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.1-6DB33F?logo=spring&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Security](https://img.shields.io/badge/Security-JWT-000000?logo=jsonwebtokens&logoColor=white)
![Observability](https://img.shields.io/badge/Tracing-Zipkin-000000)
![Resilience](https://img.shields.io/badge/Resilience-Resilience4j-4B32C3)

Cloud-native library management system built with **Spring Boot** and **Spring Cloud** microservices. It showcases service discovery, centralized configuration, API gateway routing, JWT security, resilience patterns, and distributed tracing.

---

## Highlights

- **Service discovery** via **Eureka**
- **Centralized configuration** with **Spring Cloud Config** (native mode, local `config-repo/`)
- **API gateway** using **Spring Cloud Gateway** (`lb://SERVICE-NAME` routing)
- **Authentication & authorization** with **JWT** and role-based access control
- **Resilience** with **Resilience4j** (circuit breaker, retry, timeouts, fallbacks)
- **Observability** with **Micrometer Tracing** and **Zipkin**
- **Swagger/OpenAPI** docs exposed by business services

## Overview

The system is composed of six Spring Boot services plus supporting infrastructure:

```text
Client
  -> Gateway Service
     -> Auth Service
     -> Library Service (2 runtime replicas via Eureka)
        -> Inventory Service

Supporting infrastructure:
  - Discovery Server (Eureka)
  - Config Server
  - MySQL (auth_db, library_db, inventory_db)
  - Zipkin
```

**Domain split**

- `library-service`: library and book management
- `inventory-service`: branch-level stock, reservations, and availability

A key cross-service flow is **`GET /api/books/{id}/availability`**:

1. `library-service` resolves book metadata locally
2. `library-service` calls `inventory-service` to aggregate stock by branch

## Architecture

| Component | Port | Responsibility |
|---|---:|---|
| Discovery Server | `8761` | Eureka service registry |
| Config Server | `8888` | Centralized configuration from `config-repo/` |
| Gateway Service | `8080` internally, `8085` on this host by default | Entry point, static member UI, JWT validation, route forwarding |
| Auth Service | `8084` | Signup/login, optional OAuth2 login, token renewal |
| Library Service (Instance 1) | `8081` | Library/book APIs, availability lookup |
| Library Service (Instance 2) | `8082` on this host, `8081` in-container | Second `library-service` replica for discovery/load-balancing demos |
| Inventory Service | `8083` | Inventory APIs, branch stock, reserve/return operations |
| Zipkin | `9411` | Distributed tracing UI |
| MySQL Auth | `3308` | `auth_db` |
| MySQL Library | `3306` | `library_db` |
| MySQL Inventory | `3307` | `inventory_db` |

**Gateway routes**

- `/api/libraries/**` → `LIBRARY-SERVICE`
- `/api/books/**` → `LIBRARY-SERVICE`
- `/api/inventory/**` → `INVENTORY-SERVICE`

## Tech Stack

| Technology | Version |
|---|---|
| Java | `23` |
| Maven | `3.9+` recommended |
| Spring Boot | `3.5.13` |
| Spring Cloud | `2025.0.1` |
| MySQL | `8.0.45` |

## Repository Structure

```text
.
├── pom.xml
├── docker-compose.yml
├── ZIPKIN-GUIDE.md
├── config-repo/
├── scripts/
├── discovery-server/
├── config-server/
├── gateway-service/
├── auth-service/
├── library-service/
├── inventory-service/
└── docs/
```

**Module summary**

- `discovery-server/`: Eureka server
- `config-server/`: Spring Cloud Config server
- `gateway-service/`: API gateway + static member UI + JWT validation
- `auth-service/`: password auth (and optional OAuth2) + token renewal
- `library-service/`: library/book APIs + inventory lookup client
- `inventory-service/`: inventory APIs + stock operations
- `config-repo/`: externalized YAML config consumed by the Config Server
- `scripts/`: database bootstrap SQL + `demo-check.ps1` startup verification

## Quick Start

### Prerequisites

- **Docker Desktop / Docker Engine**

Optional (non-Docker local development):

- Java 23+
- Maven 3.9+

By default, the gateway is exposed at `http://localhost:8085`.

### Build and run

```powershell
docker compose up --build -d
```

The first run builds service images from source inside Docker, which may take a few minutes.

If a local app is already using a port, copy `.env.example` to `.env` and adjust host ports before starting.

### Verify startup

```powershell
docker compose ps
curl http://localhost:8761/eureka/apps
curl http://localhost:8085/actuator/health/readiness
```

Useful URLs:

- Eureka: `http://localhost:8761`
- Config Server: `http://localhost:8888`
- Gateway: `http://localhost:8085`
- Auth Swagger: `http://localhost:8084/swagger-ui.html`
- Library Swagger: `http://localhost:8081/swagger-ui.html`
- Library Swagger (Replica 2): `http://localhost:8082/swagger-ui.html`
- Inventory Swagger: `http://localhost:8083/swagger-ui.html`
- Zipkin: `http://localhost:9411`

## Authentication

No users are seeded by default. Create a member via `/auth/signup`, then use `/auth/login` as needed.

```powershell
$Email = "member-demo-$(Get-Date -Format yyyyMMddHHmmss)@library.local"
$SignupBody = @{
  fullName = "Member Example"
  email = $Email
  password = "Library123"
} | ConvertTo-Json

$TOKEN = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/signup" `
  -ContentType "application/json" `
  -Body $SignupBody).access_token
```

**Gateway access rules**

| Request Type | Role Required |
|---|---|
| `GET /api/**` | `ROLE_USER` or `ROLE_ADMIN` |
| `POST /api/**` | `ROLE_ADMIN` |
| `PUT /api/**` | `ROLE_ADMIN` |
| `DELETE /api/**` | `ROLE_ADMIN` |
| `/auth/**` | public |
| `/actuator/health/**` | public |

Example request:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/libraries" -Headers @{Authorization="Bearer $TOKEN"}
```

## Example API Flow

```powershell
# 1) Create a member and capture a JWT
$Email = "readme-flow-$(Get-Date -Format yyyyMMddHHmmss)@library.local"
$SignupBody = @{
  fullName = "README Flow Member"
  email = $Email
  password = "Library123"
} | ConvertTo-Json

$UserToken = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/signup" `
  -ContentType "application/json" `
  -Body $SignupBody).access_token

# 2) Read a seeded book through the gateway
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1" -Headers @{Authorization="Bearer $UserToken"}

# 3) Query cross-service availability through the gateway
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" -Headers @{Authorization="Bearer $UserToken"}
```

Request flow:

```text
client -> gateway-service -> library-service -> inventory-service
```

## Configuration and Data

- Shared defaults: `config-repo/application.yml`
- Service-specific config:
  - `config-repo/library-service.yml`
  - `config-repo/inventory-service.yml`
  - `config-repo/gateway-service.yml`
- Database bootstrap scripts:
  - `scripts/init-auth-db.sql`
  - `scripts/init-library-db.sql`
  - `scripts/init-inventory-db.sql`
- Pre-demo readiness & smoke checks:
  - `scripts/demo-check.ps1`

Seeded demo record:

- Book ID `1`: `Clean Code`
- ISBN: `978-0-13-468599-1`

## Resilience and Observability

`library-service` protects calls to `inventory-service` using a load-balanced `WebClient` plus Resilience4j.

Common endpoints:

- Readiness health: `http://localhost:8085/actuator/health/readiness`
- Zipkin UI: `http://localhost:9411`

## Documentation

- `ZIPKIN-GUIDE.md`: tracing workflow and troubleshooting
- `docs/architecture/README.md`: architecture diagrams and sources
- `docs/architecture/SECURITY-ARCHITECTURE-AND-STORAGE.md`: security boundaries and storage notes

## Testing

Module-level tests are included:
- `library-service`: `BookRepositoryTest` and `BookServiceTest`
- `gateway-service`: `RequiredServicesHealthIndicatorTest`