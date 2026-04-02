# 📚 Library Microservices - Cloud Native

Cloud-native library management system built as a Spring Boot / Spring Cloud multi-module project.

The system demonstrates:

- service discovery with Eureka
- centralized configuration with Spring Cloud Config
- gateway-based routing with Spring Cloud Gateway
- inter-service communication between business services
- resilience with Resilience4j circuit breaker, retry, and timeout
- JWT-based API security with role-based access control
- distributed tracing with Micrometer and Zipkin

## 🌐 Overview

The repository contains six Spring Boot services:

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

The business domain is split into:

- `library-service`: manages libraries and books
- `inventory-service`: manages branch-level stock and availability

In the default local deployment, Service A is represented by two `library-service`
containers behind the same Eureka service ID. The gateway still routes through
`lb://LIBRARY-SERVICE`, but Eureka can now hand traffic to either replica.

The main cross-service use case is `GET /api/books/{id}/availability`, where the Library Service fetches book metadata locally and calls the Inventory Service to aggregate stock information.

## 🏗️ Architecture

| Component | Port | Responsibility |
|---|---:|---|
| Discovery Server | `8761` | Eureka service registry |
| Config Server | `8888` | Centralized configuration from local `config-repo/` |
| Gateway Service | `8080` internally, `8085` on this host by default | Main entry point, static member UI, JWT validation, route forwarding |
| Auth Service | `8084` | Password signup/login, optional OAuth2 login, JWT renewal |
| Library Service (Instance 1) | `8081` | Library and book CRUD, availability lookup |
| Library Service (Instance 2) | `8082` on this host, `8081` in-container | Second Service A replica for discovery/load-balancing demos |
| Inventory Service | `8083` | Inventory CRUD, branch stock, reserve/return operations |
| Zipkin | `9411` | Distributed tracing UI |
| MySQL Auth | `3308` | `auth_db` |
| MySQL Library | `3306` | `library_db` |
| MySQL Inventory | `3307` | `inventory_db` |

Gateway routes:

- `/api/libraries/**` -> `LIBRARY-SERVICE`
- `/api/books/**` -> `LIBRARY-SERVICE`
- `/api/inventory/**` -> `INVENTORY-SERVICE`

## ✨ Key Features

- Maven multi-module parent project with shared dependency management
- Config Server running in `native` mode, backed by the local `config-repo/` directory
- Service registration and discovery through Eureka
- Gateway-based API routing using `lb://SERVICE-NAME`
- Resilient Library -> Inventory calls via:
  - circuit breaker
  - fail-fast retry
  - bounded client timeouts and fallback handling
- JWT security through `auth-service` for password signup/login, optional OAuth2, and token renewal
- Swagger/OpenAPI docs on `auth-service`, `library-service`, and `inventory-service`
- Zipkin tracing across gateway and service hops, including the `library-service -> inventory-service` `WebClient` hop
- Sample MySQL seed data for libraries, books, and inventory

## 🛠️ Tech Stack

| Technology | Version |
|---|---|
| Java | `23` |
| Maven | `3.9+` recommended |
| Spring Boot | `3.5.13` |
| Spring Cloud | `2025.0.1` |
| MySQL | `8.0.45` |
| Spring Cloud Gateway | via Spring Cloud BOM |
| Resilience4j | via Spring Cloud circuit breaker starter |
| Micrometer Tracing + Zipkin | enabled across services |

## 📁 Repository Structure

```text
.
├── pom.xml
├── docker-compose.yml
├── RUN-AND-DEMO-GUIDE.md
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

Module summary:

- `discovery-server/`: Eureka server
- `config-server/`: Spring Cloud Config server
- `gateway-service/`: API gateway, static member UI, and JWT validation
- `auth-service/`: password signup/login, optional OAuth2 login, and JWT renewal
- `library-service/`: library/book APIs plus inventory lookup client
- `inventory-service/`: inventory APIs and stock operations
- `config-repo/`: externalized YAML config consumed by Config Server
- `scripts/`: MySQL bootstrap SQL plus `demo-check.ps1` for pre-demo verification

## 🚀 Quick Start

### ✅ Prerequisites

- Docker Desktop / Docker Engine

Optional for local non-Docker work:

- Java 23+
- Maven 3.9+

The checked-in Compose defaults are enough for a fresh clone to run without creating a `.env` file. By default, the gateway is exposed on `http://localhost:8085`.

Create a local `.env` file only if you want to override the default host ports or add your own social login credentials. The intended flow is:

- fresh clone -> `docker compose up --build -d`
- optional `.env` -> local overrides only

### ▶️ Build and Run Everything

From a fresh clone:

```powershell
docker compose up --build -d
```

The first run builds the Spring Boot service images from source inside Docker, so it can take a few minutes. Zipkin is still part of the Compose stack, but it is pulled as an external image instead of being built from this repository.

If another local app is already using a port, copy `.env.example` to `.env` and adjust the host ports before starting.

### 🔎 Verify Startup

```powershell
docker compose ps
curl http://localhost:8761/eureka/apps
curl http://localhost:8085/actuator/health/readiness
```

The Compose stack now uses readiness probes and `depends_on: condition: service_healthy`. `.\scripts\demo-check.ps1` is a point-in-time smoke check, not a readiness poller, so run it after the core services already show `healthy`.

Useful URLs:

- 🔍 Eureka: `http://localhost:8761`
- ⚙️ Config Server: `http://localhost:8888`
- 🚪 Gateway: `http://localhost:8085`
- 🔐 Auth Swagger: `http://localhost:8084/swagger-ui.html`
- 📚 Library Swagger: `http://localhost:8081/swagger-ui.html`
- 📚 Library Swagger (Replica 2): `http://localhost:8082/swagger-ui.html`
- 📦 Inventory Swagger: `http://localhost:8083/swagger-ui.html`
- 🔭 Zipkin: `http://localhost:9411`

## 🔐 Authentication

The gateway now exposes password-based auth endpoints for members:

No auth users are seeded by default. Create a fresh member through `/auth/signup`, or log in only after that account already exists.

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

Use `/auth/login` with the same email and password after the account exists.

Gateway access rules:

| Request Type | Role Required |
|---|---|
| `GET /api/**` | `ROLE_USER` or `ROLE_ADMIN` |
| `POST /api/**` | `ROLE_ADMIN` |
| `PUT /api/**` | `ROLE_ADMIN` |
| `DELETE /api/**` | `ROLE_ADMIN` |
| `/auth/**` | public |
| `/actuator/health/**` | public |

Example:

```powershell
$Email = "readme-demo-$(Get-Date -Format yyyyMMddHHmmss)@library.local"
$SignupBody = @{
  fullName = "README Demo Member"
  email = $Email
  password = "Library123"
} | ConvertTo-Json

$UserToken = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/signup" `
  -ContentType "application/json" `
  -Body $SignupBody).access_token
Invoke-RestMethod -Uri "http://localhost:8085/api/libraries" -Headers @{Authorization="Bearer $UserToken"}
```

## 🔄 Example API Flow

```powershell
# 1. Create a fresh member and capture a JWT
$Email = "readme-flow-$(Get-Date -Format yyyyMMddHHmmss)@library.local"
$SignupBody = @{
  fullName = "README Flow Member"
  email = $Email
  password = "Library123"
} | ConvertTo-Json

$UserToken = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/signup" `
  -ContentType "application/json" `
  -Body $SignupBody).access_token

# 2. Read a seeded book through the gateway
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1" -Headers @{Authorization="Bearer $UserToken"}

# 3. Query cross-service availability through the gateway
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" -Headers @{Authorization="Bearer $UserToken"}
```

This is the main request flow:

```text
client -> gateway-service -> library-service -> inventory-service
```

## ⚙️ Configuration and Data

- Shared defaults live in `config-repo/application.yml`
- Service-specific config lives in:
  - `config-repo/library-service.yml`
  - `config-repo/inventory-service.yml`
  - `config-repo/gateway-service.yml`
- The Config Server uses the `native` profile and reads from the local filesystem
- Database bootstrap scripts:
  - `scripts/init-auth-db.sql`
  - `scripts/init-library-db.sql`
  - `scripts/init-inventory-db.sql`
- Pre-demo smoke check:
  - `scripts/demo-check.ps1`

Sample data is already inserted for multiple libraries, a larger seeded catalog, and inventory across multiple branches. The currently verified demo record is:

- 📖 Book ID `1`: `Clean Code`
- 🔢 ISBN: `978-0-13-468599-1`
- 📦 Availability is populated through `inventory-service`

## 📈 Resilience and Observability

The `library-service` protects calls to `inventory-service` with a load-balanced, Boot-customized `WebClient` plus Resilience4j:

- sliding window size: `10`
- failure rate threshold: `50%`
- wait duration in open state: `10s`
- retry attempts: `1`
- Reactor Netty connect timeout: `1000ms`
- Reactor Netty response timeout: `3000ms`

Startup stability is also implemented now:

- Docker Compose health checks use `/actuator/health/readiness`
- service startup ordering uses `depends_on: condition: service_healthy`
- `gateway-service` readiness includes a custom `requiredServices` indicator, so it is only marked healthy after `AUTH-SERVICE`, `LIBRARY-SERVICE`, and `INVENTORY-SERVICE` are visible in discovery

Useful endpoints:

- ❤️ `http://localhost:8085/actuator/health/readiness`
- ❤️ `http://localhost:8081/actuator/health/readiness`
- 🔌 `http://localhost:8081/actuator/circuitbreakers` (requires auth)
- 🔭 `http://localhost:9411`

## 📖 Documentation

Detailed project docs already in the repo:

- [`RUN-AND-DEMO-GUIDE.md`](./RUN-AND-DEMO-GUIDE.md): startup, API demo flow, and browser demo flow in one combined guide
- [`docs/architecture/README.md`](./docs/architecture/README.md): architecture diagrams and source assets
- [`docs/architecture/SECURITY-ARCHITECTURE-AND-STORAGE.md`](./docs/architecture/SECURITY-ARCHITECTURE-AND-STORAGE.md): security boundaries and storage design notes

## 🧪 Testing Status

Automated module-level tests are present in the repository:

- `library-service`: `BookRepositoryTest` and `BookServiceTest` (`11` tests)
- `gateway-service`: `RequiredServicesHealthIndicatorTest` (`2` tests)

The docs still describe manual verification for the distributed happy path because there is no end-to-end automated test that covers `gateway-service -> library-service -> inventory-service`.
