# Library Microservices

> Cloud-native library management system built with Spring Boot and Spring Cloud microservices.

[![Java](https://img.shields.io/badge/Java-23-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-brightgreen?logo=spring)](https://spring.io/projects/spring-cloud)
[![Build](https://img.shields.io/badge/Build-Maven-blue?logo=apachemaven)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?logo=docker)](https://www.docker.com/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-Circuit%20Breaker-orange)](https://resilience4j.readme.io/)
[![Zipkin](https://img.shields.io/badge/Tracing-Zipkin-brightgreen)](https://zipkin.io/)

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Running Locally](#running-locally)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Contributing](#contributing)

---

## Overview

This repository implements a cloud-native library management system as a Spring Boot / Spring Cloud multi-module Maven project. It is structured to demonstrate real-world microservices patterns including service discovery, centralised configuration, gateway-based routing, inter-service communication, resilience, JWT security, and distributed tracing.

**Request flow:**

```text
Client
  └─▶ Gateway Service (8085)
        ├─▶ Auth Service      (8084)  — /auth/**
        ├─▶ Library Service   (8081 / 8082)  — /api/libraries/**, /api/books/**
        │     └─▶ Inventory Service (8083)  — internal WebClient call
        └─▶ Inventory Service (8083)  — /api/inventory/**

Infrastructure:
  Discovery Server  (8761)  — Eureka
  Config Server     (8888)  — Spring Cloud Config (native profile)
  MySQL             (3306 / 3307 / 3308)  — library_db / inventory_db / auth_db
  Zipkin            (9411)  — distributed traces
```

Two `library-service` replicas register under the same Eureka service ID (`LIBRARY-SERVICE`). The gateway routes via `lb://LIBRARY-SERVICE`, letting Eureka load-balance across both replicas.

---

## Features

- **Maven multi-module** parent with shared BOM-managed dependency versions
- **Spring Cloud Config** running in `native` mode, backed by the local `config-repo/` directory
- **Eureka** service registration and discovery across all services
- **Spring Cloud Gateway** API routing with `lb://SERVICE-NAME` load-balanced URIs and per-route circuit breakers
- **Resilience4j** protecting Library → Inventory `WebClient` calls: circuit breaker, retry, and Reactor Netty connect/response timeouts
- **JWT security** via `auth-service`: password-based signup/login, optional OAuth2 social login (Google, GitHub, Facebook), and token renewal
- **Role-based access control** enforced at the gateway (`ROLE_USER` for reads, `ROLE_ADMIN` for writes)
- **Swagger / OpenAPI** UI on `auth-service`, `library-service`, and `inventory-service`
- **Micrometer + Zipkin** distributed tracing across all gateway and service hops
- **Ordered startup** using Docker Compose `depends_on: condition: service_healthy` and `/actuator/health/readiness` probes
- **Custom gateway readiness indicator** that waits until `AUTH-SERVICE`, `LIBRARY-SERVICE`, and `INVENTORY-SERVICE` are visible in Eureka
- **Seeded sample data** for libraries, books, and multi-branch inventory

---

## Tech Stack

| Technology | Version |
|---|---|
| Java | 23 |
| Maven | 3.9+ |
| Spring Boot | 4.0.5 |
| Spring Cloud | 2025.1.1 |
| Spring Cloud Gateway | via Spring Cloud BOM |
| Resilience4j | via Spring Cloud circuit breaker starter |
| JJWT | 0.12.6 |
| MySQL | 8.0.45 |
| Micrometer Tracing + Zipkin | via Spring Boot BOM |
| Springdoc OpenAPI | via Spring Boot BOM |
| Testcontainers | 1.20.4 |
| Docker / Docker Compose | any current release |

---

## Getting Started

### Prerequisites

- [Docker Desktop](https://docs.docker.com/get-docker/) (or Docker Engine + Compose plugin)

For running or building outside Docker:

- Java 23+
- Maven 3.9+

### Installation

```bash
git clone https://github.com/flaviu-vanca/library-microservices.git
cd library-microservices
```

No additional configuration is required for a standard local run. All default ports and credentials are baked into `docker-compose.yml`.

If any default port conflicts with a running service on your machine, copy `.env.example` to `.env` and override the relevant port variables before starting.

### Running Locally

```bash
docker compose up --build -d
```

The first build compiles all six Spring Boot services inside Docker; expect a few minutes. Subsequent starts reuse cached layers.

**Verify the stack is ready:**

```bash
docker compose ps
curl http://localhost:8761/eureka/apps
curl http://localhost:8085/actuator/health/readiness
```

The gateway's readiness probe only returns `UP` once Eureka reports all three required services (`AUTH-SERVICE`, `LIBRARY-SERVICE`, `INVENTORY-SERVICE`). A brief `Finalizing: service discovery` phase is expected while the second Library Service replica finishes registering.

**Run the pre-demo smoke check (PowerShell):**

```powershell
.\scripts\demo-check.ps1
# Optional: .\scripts\demo-check.ps1 -StartupTimeoutSeconds 300 -PollIntervalSeconds 10
```

Or double-click `Run Demo Check.cmd` from Windows Explorer.

**Service URLs:**

| Service | URL |
|---|---|
| Eureka | <http://localhost:8761> |
| Config Server API | <http://localhost:8888/library-service/default> |
| Gateway | <http://localhost:8085> |
| Auth Swagger | <http://localhost:8084/swagger-ui.html> |
| Library Swagger (instance 1) | <http://localhost:8081/swagger-ui.html> |
| Library Swagger (instance 2) | <http://localhost:8082/swagger-ui.html> |
| Inventory Swagger | <http://localhost:8083/swagger-ui.html> |
| Zipkin | <http://localhost:9411> |

**Stop the stack:**

```bash
docker compose down
```

---

## API Reference

All business API calls pass through the gateway on port `8085`.

### Authentication

No users are seeded. Create an account first, then use the returned JWT as a Bearer token.

```bash
# Sign up
curl -s -X POST http://localhost:8085/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Demo User","email":"demo@library.local","password":"Library123"}'

# Log in (re-issue a token for an existing account)
curl -s -X POST http://localhost:8085/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@library.local","password":"Library123"}'

# Renew an expiring token (within the last 5 minutes of the session)
curl -s -X POST http://localhost:8085/auth/renew \
  -H "Authorization: Bearer <token>"
```

#### Gateway access rules

| Pattern | Minimum role |
|---|---|
| `GET /api/**` | `ROLE_USER` |
| `POST /api/**` | `ROLE_ADMIN` |
| `PUT /api/**` | `ROLE_ADMIN` |
| `DELETE /api/**` | `ROLE_ADMIN` |
| `/auth/**` | public |
| `/actuator/health/**` | public |

### Libraries — `/api/libraries`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/libraries` | List all libraries |
| `GET` | `/api/libraries/{id}` | Get library by ID |
| `GET` | `/api/libraries/search?city={city}` | Search libraries by city |
| `POST` | `/api/libraries` | Create a library (admin) |
| `PUT` | `/api/libraries/{id}` | Update a library (admin) |
| `DELETE` | `/api/libraries/{id}` | Delete a library (admin) |

### Books — `/api/books`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/books` | List all books |
| `GET` | `/api/books/{id}` | Get book by ID |
| `GET` | `/api/books/isbn/{isbn}` | Get book by ISBN |
| `GET` | `/api/books/{id}/availability` | Book metadata + inventory status (cross-service call) |
| `GET` | `/api/books/search?q=&isbn=&year=&genre=` | Search books |
| `GET` | `/api/books/library/{libraryId}` | Books in a specific library |
| `POST` | `/api/books` | Create a book (admin) |
| `PUT` | `/api/books/{id}` | Update a book (admin) |
| `DELETE` | `/api/books/{id}` | Delete a book (admin) |

### Inventory — `/api/inventory`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/inventory/{isbn}` | Aggregated stock status across all branches |
| `GET` | `/api/inventory/{isbn}/branches` | Per-branch stock breakdown |
| `GET` | `/api/inventory/branch/{branchId}` | All items at a branch |
| `GET` | `/api/inventory/item/{id}` | Get inventory item by ID |
| `POST` | `/api/inventory` | Create inventory record (admin) |
| `PUT` | `/api/inventory/item/{id}` | Update inventory item (admin) |
| `DELETE` | `/api/inventory/item/{id}` | Delete inventory item (admin) |
| `POST` | `/api/inventory/{isbn}/reserve` | Reserve one copy at a branch (admin) |
| `POST` | `/api/inventory/{isbn}/return` | Return one copy at a branch (admin) |

### Example end-to-end flow (curl)

```bash
# 1. Sign up and capture the JWT (requires jq)
TOKEN=$(curl -s -X POST http://localhost:8085/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Flow Demo","email":"flow@library.local","password":"Library123"}' \
  | jq -r '.access_token')

# 2. Fetch a seeded book
curl -s http://localhost:8085/api/books/1 \
  -H "Authorization: Bearer $TOKEN"

# 3. Cross-service availability lookup (Library -> Inventory)
curl -s http://localhost:8085/api/books/1/availability \
  -H "Authorization: Bearer $TOKEN"
```

---

## Project Structure

```text
repo-root/
├── pom.xml                        # Parent POM — BOM, shared deps, plugin config
├── docker-compose.yml             # Full stack: services + infra + health ordering
├── Dockerfile.service             # Multi-stage build for all Spring Boot modules
├── .env.example                   # Port / credential overrides (copy → .env to use)
├── ZIPKIN-GUIDE.md
│
├── config-repo/                   # Externalized YAML consumed by Config Server
│   ├── application.yml            # Shared defaults (Eureka, tracing, security)
│   ├── gateway-service.yml        # Routes, circuit breakers, readiness config
│   ├── library-service.yml
│   ├── inventory-service.yml
│   └── auth-service.yml
│
├── scripts/
│   ├── init-auth-db.sql
│   ├── init-library-db.sql
│   ├── init-inventory-db.sql
│   └── demo-check.ps1             # Pre-demo smoke check with Eureka convergence wait
│
├── discovery-server/              # Eureka service registry
├── config-server/                 # Spring Cloud Config (native profile)
├── gateway-service/               # API gateway, JWT validation, circuit breakers
├── auth-service/                  # Signup / login / OAuth2 / JWT renewal
├── library-service/               # Library & book CRUD, availability aggregation
├── inventory-service/             # Inventory CRUD, reserve / return operations
│
└── docs/
    └── architecture/              # Architecture documentation
        ├── drawio/                # Editable Draw.io source files
        └── img/                   # Exported PNG diagrams
```

---

## Contributing

1. Fork the repository and create a feature branch (`git checkout -b feature/my-change`).
2. Make your changes and verify them with the existing test suite (`mvn test`) and `scripts/demo-check.ps1` against a running stack.
3. Open a pull request with a clear description of the change and its motivation.
