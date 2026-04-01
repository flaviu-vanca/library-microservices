# Security, Trust Boundaries, and Storage

This document summarizes the current security and persistence model in the project and highlights the main hardening steps that would matter in a production deployment.

## Current Security Model

- `auth-service` issues JWTs for password signup and login
- `gateway-service` validates bearer tokens on protected routes
- downstream services revalidate propagated JWTs
- browser clients store and resend bearer tokens for authenticated API calls

## Trust Boundaries

### External client to gateway

- public traffic enters through `gateway-service`
- protected API routes require a valid JWT
- unauthenticated requests are rejected at the edge

### Gateway to business services

- the gateway forwards authenticated traffic to `library-service` and `inventory-service`
- downstream services do not trust the gateway blindly; they validate the propagated token again

### Service-to-service calls

- `library-service` calls `inventory-service` for availability aggregation
- the downstream call executes within the authenticated request flow and preserves the current security context

## Storage Boundaries

The project keeps service data separated by responsibility:

- `auth-service` -> `auth_db`
- `library-service` -> `library_db`
- `inventory-service` -> `inventory_db`

This avoids shared business tables across services and keeps ownership of writes local to each bounded context.

## Why This Is Reasonable for the Current Project

- JWTs remove the need for shared session storage
- separate MySQL databases keep service ownership clear
- gateway enforcement plus downstream validation gives a simple but defensible trust model for a local Docker deployment

## Production Hardening Gaps

The current model is appropriate for the repository and local demos, but a stronger production posture would likely add:

- tighter secret management for JWT signing material
- shorter token lifetimes and stronger revocation controls
- narrower direct exposure of downstream services
- stronger internal service identity, for example gateway-issued internal tokens or mTLS between services
- a server-side session strategy if browser token handling becomes a concern

## Related Files

- `gateway-service/src/main/java/com/tus/microservices/gateway/security`
- `auth-service/src/main/java/com/tus/microservices/auth/security`
- `library-service/src/main/java/com/tus/microservices/library/security`
- `inventory-service/src/main/java/com/tus/microservices/inventory/security`
