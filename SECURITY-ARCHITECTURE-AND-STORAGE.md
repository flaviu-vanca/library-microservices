# Security Architecture and Storage

## Purpose

This document describes the current security architecture, trust boundaries, and
storage model implemented in `library-microservices-cloud-native`.

It covers:

- authentication and authorization
- JWT issuance, validation, and propagation
- browser-to-service trust boundaries
- service-owned data storage
- operational security considerations
- current limitations and recommended hardening steps

This is a current-state architecture reference for the repository as it exists
today.

---

## System Overview

The application is a Spring Cloud microservices system composed of:

- `gateway-service`
- `auth-service`
- `library-service`
- `inventory-service`
- `config-server`
- `discovery-server`

Supporting infrastructure:

- MySQL for service-owned relational data
- Zipkin for distributed tracing
- Docker Compose for local orchestration

The main user-facing flow is:

```text
Browser
  -> Gateway UI
  -> auth-service for signup/login
  -> JWT returned to browser
  -> authenticated API calls through gateway
  -> gateway routes to library-service and inventory-service
```

The main cross-service business flow is:

```text
client -> gateway-service -> library-service -> inventory-service
```

Specifically, `GET /api/books/{id}/availability` reads book metadata from
`library-service` and live stock data from `inventory-service`.

---

## Security Architecture

## Authentication Model

The current authentication model is JWT-based and is owned by
`auth-service`.

Public authentication endpoints:

- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/renew`
- `GET /auth/info`
- OAuth2 provider initiation and callback endpoints under `/auth/**`

Two login paths are supported.

### 1. Email and password

Flow:

```text
Browser -> Gateway -> auth-service
auth-service -> create or verify local account
auth-service -> issue JWT
browser -> store JWT and send Bearer token on later API requests
```

Behavior:

- `signup` creates a `USER` account
- `login` verifies a BCrypt password hash
- `renew` allows one token extension during the final 5 minutes of a valid
  session

Relevant implementation:

- `auth-service/src/main/java/com/tus/microservices/auth/controller/AuthController.java`
- `auth-service/src/main/java/com/tus/microservices/auth/service/AuthenticationService.java`
- `auth-service/src/main/java/com/tus/microservices/auth/service/JwtTokenService.java`
- `auth-service/src/main/java/com/tus/microservices/auth/config/PasswordConfig.java`

### 2. OAuth2 social login

Supported providers:

- Google
- GitHub
- Facebook

Flow:

```text
Browser -> /auth/oauth2/authorization/{provider}
auth-service -> external provider
provider -> auth-service callback
auth-service -> create or link local identity
auth-service -> issue the same JWT response shape used by password auth
auth-service -> redirect back to gateway with token data in the URL fragment
gateway UI -> parse fragment and persist session locally
```

OAuth2 provider availability depends on configured client credentials.

Relevant implementation:

- `auth-service/src/main/java/com/tus/microservices/auth/config/SecurityConfig.java`
- `auth-service/src/main/java/com/tus/microservices/auth/service/OAuth2AuthenticationSuccessHandler.java`
- `auth-service/src/main/java/com/tus/microservices/auth/entity/AuthIdentity.java`

---

## Password Storage

Password handling is implemented with standard Spring Security practices.

Current design:

- raw passwords are accepted only at the API boundary
- passwords are hashed with `BCryptPasswordEncoder`
- only the BCrypt hash is persisted
- OAuth-only accounts may have `password_hash = null`

Storage location:

- `auth_db.auth_users.password_hash`

What exists today:

- password hashing
- local email/password login

What does not exist today:

- password reset workflow
- password history
- lockout policy
- adaptive re-authentication

Relevant implementation:

- `auth-service/src/main/java/com/tus/microservices/auth/config/PasswordConfig.java`
- `auth-service/src/main/java/com/tus/microservices/auth/service/AuthenticationService.java`
- `scripts/init-auth-db.sql`

---

## Token Model

The current token model is a shared symmetric JWT.

### Characteristics

| Item | Current implementation |
|---|---|
| Signing algorithm | `HS256` |
| Secret source | `config-repo/application.yml` |
| Default lifetime | `30 minutes` |
| Renewal | single renewal near expiry |
| Token storage in browser | `localStorage` |
| Revocation list | not implemented |
| Refresh token pair | not implemented |
| Key rotation | not implemented |
| JWKS / asymmetric signing | not implemented |

### Issued claims

Current JWT payload includes:

- `sub`
- `roles`
- `email`
- `full_name`
- `role`
- `renewed`
- `iat`
- `exp`

### Renewal behavior

The `/auth/renew` endpoint:

- requires a valid bearer token
- allows renewal only within the last 5 minutes of the token lifetime
- allows renewal only once

Relevant implementation:

- `auth-service/src/main/java/com/tus/microservices/auth/service/JwtTokenService.java`
- `auth-service/src/main/java/com/tus/microservices/auth/config/JwtConfig.java`
- `config-repo/application.yml`

---

## Authorization Model

The application uses role-based access control with two roles:

- `USER`
- `ADMIN`

Role expansion:

- `USER` receives `ROLE_USER`
- `ADMIN` receives `ROLE_USER` and `ROLE_ADMIN`

This allows administrators to satisfy both read and write rules.

### Gateway authorization

`gateway-service` is the primary browser-facing enforcement point.

Public routes:

- `/`
- static UI assets
- `/auth/**`
- `/actuator/health`
- `/actuator/info`
- `/fallback/**`

Protected routes:

| Route pattern | Required role |
|---|---|
| `GET /api/**` | `ROLE_USER` or `ROLE_ADMIN` |
| `POST /api/**` | `ROLE_ADMIN` |
| `PUT /api/**` | `ROLE_ADMIN` |
| `DELETE /api/**` | `ROLE_ADMIN` |

Relevant implementation:

- `gateway-service/src/main/java/com/tus/microservices/gateway/config/SecurityConfig.java`

### Library-service authorization

`library-service` validates JWTs independently and enforces the same core
read/write split:

- public health/info
- public Swagger and API docs
- `GET /api/**` for `USER` or `ADMIN`
- writes for `ADMIN`

Relevant implementation:

- `library-service/src/main/java/com/tus/microservices/library/config/SecurityConfig.java`

### Inventory-service authorization

`inventory-service` also validates JWTs independently.

Rules:

- public health/info
- public Swagger and API docs
- `GET /api/**` for `USER` or `ADMIN`
- reserve/return endpoints for `USER` or `ADMIN`
- other writes for `ADMIN`

Relevant implementation:

- `inventory-service/src/main/java/com/tus/microservices/inventory/config/SecurityConfig.java`

### Auth-service authorization

`auth-service` intentionally exposes only auth and operational endpoints:

- `/auth/**`
- health/info
- Swagger and API docs

All other requests are denied.

Relevant implementation:

- `auth-service/src/main/java/com/tus/microservices/auth/config/SecurityConfig.java`

---

## Trust Boundaries

The current system has four important trust boundaries.

### 1. Browser -> Gateway

The browser communicates with the gateway using:

- unauthenticated requests for UI assets and `/auth/**`
- authenticated requests with `Authorization: Bearer <JWT>` for `/api/**`

The gateway validates the token and enforces route-level RBAC.

### 2. Gateway -> Auth Service

Authentication operations are routed through the gateway to `auth-service`.

The gateway does not authenticate end users itself. It delegates user creation,
password verification, token issuance, and OAuth2 completion to `auth-service`.

### 3. Gateway -> Library / Inventory Services

The gateway forwards authenticated requests to downstream services. Downstream
services do not simply trust the gateway; they validate the JWT again using the
same shared secret.

This provides defense in depth, but it also means a compromise of the shared
JWT secret affects all validating services.

### 4. Library Service -> Inventory Service

For availability lookups, `library-service` forwards the caller's bearer token
to `inventory-service`.

This means the end-user token crosses an additional internal boundary instead of
being replaced by a service-scoped credential.

Relevant implementation:

- `library-service/src/main/java/com/tus/microservices/library/service/InventoryClient.java`

---

## Browser Session Handling

The system does not use server-side browser sessions for the main user flow.

Current browser-side behavior:

- the gateway serves a static frontend
- JWTs are stored in `localStorage`
- session metadata is also stored in `localStorage`
- OAuth2 callback state is delivered back through the URL fragment
- authenticated fetch requests attach `Authorization: Bearer <token>`

Relevant implementation:

- `gateway-service/src/main/resources/static/index.js`

This is acceptable for an assignment demo, but it is not the strongest browser
security model because:

- `localStorage` is accessible to injected JavaScript
- bearer tokens remain browser-managed secrets
- URL fragment token delivery increases browser-surface exposure

---

## Security Configuration Summary

### CSRF

CSRF is disabled in:

- `gateway-service`
- `auth-service`
- `library-service`
- `inventory-service`

This is consistent with the current stateless bearer-token design.

It would not be an appropriate default if the browser model changes later to
cookie-based sessions.

### Session policy

- `library-service` and `inventory-service` use `STATELESS`
- `auth-service` uses `IF_REQUIRED` because OAuth2 login requires framework
  session support during the login handshake

### Secret management

The repository currently relies on configuration-driven secrets and defaults:

- shared JWT secret in `config-repo/application.yml`
- OAuth client IDs and secrets via `.env` and environment variables
- MySQL credentials in local config / compose defaults

This is convenient for local development but should not be treated as a
production secret-management model.

---

## Storage Architecture

## Storage Model Overview

The project uses service-owned relational storage with one schema per business
boundary:

| Database | Owner | Responsibility |
|---|---|---|
| `auth_db` | `auth-service` | identities, password hashes, OAuth links |
| `library_db` | `library-service` | libraries and books |
| `inventory_db` | `inventory-service` | branch-level stock counts |

Runtime persistence is provided by Docker named volumes:

- `mysql_auth_data`
- `mysql_library_data`
- `mysql_inventory_data`

The system does not implement:

- user file uploads
- object storage
- binary blob storage
- public file-serving paths

---

## Auth Storage

`auth_db` contains the authentication and identity data.

### `auth_users`

Purpose:

- local account identity
- password hash storage
- role assignment
- account activation state

Key fields:

- `id`
- `email` (`UNIQUE`)
- `full_name`
- `password_hash`
- `role`
- `email_verified`
- `active`
- `created_at`
- `updated_at`

### `auth_oauth_identities`

Purpose:

- link one local account to an external provider identity

Key fields:

- `id`
- `auth_user_id`
- `provider`
- `provider_user_id`
- `created_at`
- `updated_at`

Key constraints:

- unique `(provider, provider_user_id)`
- foreign key to `auth_users(id)` with `ON DELETE CASCADE`

Relevant bootstrap:

- `scripts/init-auth-db.sql`

What is not stored in `auth_db` today:

- refresh tokens
- active session records
- login event audit trail
- failed-login counters
- password reset tokens
- verification token history

---

## Library Storage

`library_db` contains catalog data.

### `libraries`

Stores:

- library name
- address
- city
- country
- timestamps

### `books`

Stores:

- ISBN (`UNIQUE`)
- title
- author
- publication year
- genre
- `library_id`
- timestamps

Relationship:

- `books.library_id -> libraries.id`
- delete behavior is `ON DELETE SET NULL`

Relevant bootstrap:

- `scripts/init-library-db.sql`

Design note:

- branch-level stock is intentionally not stored here
- inventory remains a separate service concern

---

## Inventory Storage

`inventory_db` contains operational stock state.

### `inventory_items`

Stores:

- `isbn`
- `branch_id`
- `total_copies`
- `available_copies`
- `reserved_copies`
- `last_updated`

Key constraint:

- unique `(isbn, branch_id)`

Relevant bootstrap:

- `scripts/init-inventory-db.sql`

Design note:

- there is no cross-database foreign key to `library_db`
- consistency between services is based on ISBN contracts, not shared-schema
  joins

That is appropriate for this microservice boundary.

---

## Schema Management and Persistence

The local runtime uses a hybrid bootstrap model:

- MySQL initialization scripts create base schemas and seed demo data
- JPA is also configured with `ddl-auto=update`

This applies to:

- `auth-service`
- `library-service`
- `inventory-service`

Relevant configuration:

- `config-repo/auth-service.yml`
- `config-repo/library-service.yml`
- `config-repo/inventory-service.yml`

Operational implication:

- schema creation is convenient in development
- schema evolution is less controlled than a migration-based approach

For production-grade operation, explicit migrations would be preferable.

---

## Infrastructure Exposure

In the local Docker Compose setup, the following components are exposed on host
ports:

- Eureka
- Config Server
- Auth Service
- Library Service
- Inventory Service
- Zipkin
- MySQL Auth
- MySQL Library
- MySQL Inventory

This is reasonable for local development and demonstration.

For a hardened deployment, the following should be treated as internal-only
components:

- MySQL instances
- Eureka
- Config Server
- Zipkin

Relevant configuration:

- `docker-compose.yml`
- `.env`

---

## Current Strengths

- Passwords are stored as BCrypt hashes.
- OAuth2 identities are normalized into a dedicated table.
- Account email uniqueness is enforced at the database level.
- OAuth provider identity uniqueness is enforced at the database level.
- Authorization rules are explicit and consistent across gateway and services.
- Service-owned data is split into separate schemas.
- Downstream services do not blindly trust the gateway; they validate tokens
  again.
- The main cross-service user flow is traceable through Zipkin.

---

## Current Risks and Limitations

The main security limitations of the current design are:

### 1. Shared symmetric signing secret

All JWT-validating services depend on the same shared secret.

Risk:

- secret leakage at one boundary affects all JWT validators

### 2. Browser-managed bearer tokens

Tokens are stored in `localStorage`.

Risk:

- an XSS issue would expose active bearer tokens

### 3. No revocation model

Current state:

- no token blacklist
- no logout invalidation
- no session store

### 4. Limited token lifecycle management

Current state:

- fixed short-lived JWT
- one renewal path
- no refresh token rotation

### 5. Secrets are not production-managed

Current local defaults include:

- JWT secret in shared config
- OAuth provider keys through environment variables
- MySQL credentials through local config defaults

### 6. No auth abuse controls

Missing controls:

- rate limiting
- lockout policy
- failed-login counters
- security event audit trail

### 7. `ddl-auto=update` in runtime services

Risk:

- schema changes are less controlled than migration-driven releases

### 8. Host-exposed internal infrastructure

Risk:

- operational components are easier to reach than they should be outside a
  trusted development environment

---

## Recommended Hardening Roadmap

If this system is extended beyond coursework/demo use, the next improvements
should be:

1. Move secrets out of repository-backed configuration and into environment or
   secret-management infrastructure.
2. Replace shared symmetric JWT validation with asymmetric signing or a more
   centralized trust model.
3. Move the browser away from `localStorage` bearer tokens toward `HttpOnly`
   cookies and a gateway-managed session or BFF pattern.
4. Add rate limiting and lockout controls to `/auth/signup`, `/auth/login`, and
   `/auth/renew`.
5. Add security audit logging for authentication and authorization events.
6. Replace `ddl-auto=update` with explicit schema migration tooling.
7. Restrict exposure of MySQL, Eureka, Config Server, and Zipkin in non-local
   environments.

---

## Conclusion

`library-microservices-cloud-native` currently implements a coherent
assignment-grade security architecture built around:

- `auth-service` as the token issuer
- JWT-based browser authentication
- gateway and downstream JWT validation
- explicit role-based access control
- service-owned relational storage across three MySQL schemas

It is materially stronger than an unsecured demo stack, but it is not yet a
production-grade security baseline.

The most important next steps are secret hardening, browser token handling
improvements, and a more mature token lifecycle model.
