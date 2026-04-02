# Run and Demo Guide

This guide combines the three operational walkthroughs for the project in the requested order:

1. How to run the application
2. Demo API guide
3. Demo UI guide

---

## Part 1. How to Run the Application

## ✅ Prerequisites

| Requirement | Version | Verify Command |
|-------------|---------|----------------|
| 🐳 Docker Desktop | Latest | `docker --version` |

> ⚠️ **Important:** Docker Desktop must be **running** before executing any commands.

> ℹ️ **Default gateway URL:** A fresh clone exposes the gateway on `http://localhost:8085`.
> Copy `.env.example` to `.env` only if you want to override ports or add social login credentials.

Optional for local non-Docker builds and tests:

| Requirement | Version | Verify Command |
|-------------|---------|----------------|
| ☕ Java | 23+ | `java -version` |
| 🔧 Maven | 3.9+ | `mvn -version` |

---

## 🚀 One Command to Rule Them All

### ▶️ Start Everything From Source (Build + Run)

```powershell
docker compose up --build -d
```

### ▶️ Start Everything + Follow Logs

```powershell
docker compose up --build -d
docker compose logs -f
```

### 🔄 Full Reset (Stop + Rebuild + Start)

```powershell
docker compose down
docker compose up --build -d
```

### 💣 Nuclear Reset (Remove Data + Rebuild + Start)

```powershell
docker compose down -v
docker compose up --build -d
```

---

## 🛠️ Individual Commands

### 🔨 Build Only

```powershell
docker compose build
```

### ▶️ Start Only (after build)

```powershell
docker compose up -d
```

### ⏹️ Stop All Services

```powershell
docker compose down
```

### 🗑️ Stop + Remove All Data

```powershell
docker compose down -v
```

### 📋 View Logs (all services)

```powershell
docker compose logs -f
```

### 📋 View Logs (specific service)

```powershell
docker compose logs -f library-service
docker compose logs -f library-service-2
docker compose logs -f gateway-service
docker compose logs -f inventory-service
```

### 📊 Check Container Status

```powershell
docker compose ps
```

### 🔄 Restart a Single Service

```powershell
docker compose restart library-service
docker compose restart library-service-2
```

---

## 🌐 Service URLs

| Service | URL | Purpose |
|---------|-----|---------|
| 🔍 Eureka Dashboard | http://localhost:8761 | Service registry UI |
| ⚙️ Config Server | http://localhost:8888 | Configuration endpoint |
| 🚪 API Gateway | http://localhost:8085 | Main API entry point |
| 🔐 Auth Service | http://localhost:8084 | Password/OAuth2 auth endpoints |
| 📚 Library Service (Instance 1) | http://localhost:8081 | Direct access |
| 📚 Library Service (Instance 2) | http://localhost:8082 | Second Service A replica |
| 📦 Inventory Service | http://localhost:8083 | Direct access |
| 🔭 Zipkin | http://localhost:9412 | Distributed tracing UI |
| 📖 Auth Swagger | http://localhost:8084/swagger-ui.html | API docs |
| 📖 Library Swagger | http://localhost:8081/swagger-ui.html | API docs |
| 📖 Library Swagger (Replica 2) | http://localhost:8082/swagger-ui.html | API docs |
| 📖 Inventory Swagger | http://localhost:8083/swagger-ui.html | API docs |

---

## ✅ Verify Everything is Running

### 🏥 Quick Health Check

```powershell
# Check container status (all should show "Up" or "healthy")
docker compose ps

# Check Eureka has all services registered
curl http://localhost:8761/eureka/apps
```

Docker Compose now waits on `/actuator/health/readiness` for the main services. For a clean demo start, prefer `healthy` over just `Up` in `docker compose ps`.

### 💓 Individual Health Endpoints

```powershell
curl http://localhost:8761/actuator/health/readiness
curl http://localhost:8888/actuator/health/readiness
curl http://localhost:8084/actuator/health/readiness
curl http://localhost:8085/actuator/health/readiness
curl http://localhost:8081/actuator/health/readiness
curl http://localhost:8082/actuator/health/readiness
curl http://localhost:8083/actuator/health/readiness
```

Each endpoint should report `status=UP`. The gateway readiness response now also includes a `requiredServices` component that must be `UP` before the gateway is considered demo-ready.

Verify Eureka shows two `LIBRARY-SERVICE`
instances, not just the service name once.

---

## 🔐 JWT Authentication

### 📝 Create a Member Account

```powershell
$SignupBody = @{
  fullName = "Member Example"
  email = "member@example.com"
  password = "Library123"
} | ConvertTo-Json

$TOKEN = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/signup" `
  -ContentType "application/json" `
  -Body $SignupBody).access_token
```

### 🔑 Log In With an Existing Account

```powershell
$LoginBody = @{
  email = "member@example.com"
  password = "Library123"
} | ConvertTo-Json

$TOKEN = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/login" `
  -ContentType "application/json" `
  -Body $LoginBody).access_token
```

### 📤 Use Token in Requests

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/libraries" -Headers @{Authorization="Bearer $TOKEN"}

# Or with curl
curl -H "Authorization: Bearer YOUR_TOKEN_HERE" http://localhost:8085/api/libraries
```

### 🛡️ Access Control

| Role | GET | POST | PUT | DELETE |
|------|-----|------|-----|--------|
| 👤 ROLE_USER | ✅ | ❌ | ❌ | ❌ |
| 👑 ROLE_ADMIN | ✅ | ✅ | ✅ | ✅ |

---

## 🔧 Troubleshooting

### ❌ Problem: Services crash on startup

**Symptom:** `docker compose ps` shows services as "Exited"

**Solution:**
```powershell
# Check the logs for the failed service
docker compose logs library-service

# Usually a config issue - restart after config-server is healthy
docker compose restart library-service library-service-2 inventory-service gateway-service
```

Then wait until `docker compose ps` shows the restarted services as `healthy` again.

---

### ❌ Problem: Cannot connect to Docker

**Symptom:** `error during connect: ... pipe\dockerDesktopLinuxEngine`

**Solution:** 🐳 Start Docker Desktop from the Start Menu and wait for it to fully load.

---

### ❌ Problem: Port already in use

**Symptom:** `Port 8085 already in use` or similar

**Solution (PowerShell):**
```powershell
# Find and kill process on specific port
$port = 8085
$pid = (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue).OwningProcess
if ($pid) { Stop-Process -Id $pid -Force }
```

---

### 💀 Kill Switch: Free All Ports

```powershell
@(8761,8888,8085,8084,8081,8082,8083,3306,3307,3308,9412) | ForEach-Object {
    $p = Get-NetTCPConnection -LocalPort $_ -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess
    if ($p) { Stop-Process -Id $p -Force -ErrorAction SilentlyContinue }
}
```

---

### ❌ Problem: Services not registering with Eureka

**Symptom:** Eureka dashboard shows only CONFIG-SERVER

**Solution:**
```powershell
# Wait for readiness to turn healthy first
docker compose ps
curl http://localhost:8085/actuator/health/readiness

# If services still do not appear after startup settles, restart the missing service
docker compose restart auth-service library-service library-service-2 inventory-service gateway-service
```

---

### ❌ Problem: 401 Unauthorized

**Symptom:** API returns 401

**Solution:** Include JWT token in request header:
```powershell
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8085/api/libraries
```

---

### ❌ Problem: 403 Forbidden

**Symptom:** API returns 403 on POST/PUT/DELETE

**Solution:** POST/PUT/DELETE require an ADMIN account. Provision the admin user in `auth_db`, then log in through the same password-based `/auth/login` endpoint.

```powershell
$AdminLoginBody = @{
  email = "admin@example.com"
  password = "Admin123"
} | ConvertTo-Json

curl -Method Post -Uri "http://localhost:8085/auth/login" `
  -ContentType "application/json" `
  -Body $AdminLoginBody
```

---

### ❌ Problem: Config Server returns 404

**Symptom:** `curl http://localhost:8888` returns 404

**Solution:** This is normal - use the correct endpoint:
```powershell
curl http://localhost:8888/library-service/default
curl http://localhost:8888/actuator/health
```

---

## 📊 Startup Order (Handled by Docker Compose)

```text
┌─────────────────────────────────────────────────────────┐
│  1. 🗄️  MySQL (auth + library + inventory) + Zipkin    │  ← Infrastructure
│                        ↓                                │
│  2. 🔍 Discovery Server (:8761)                        │  ← Service Registry
│                        ↓                                │
│  3. ⚙️  Config Server (:8888)                          │  ← Configuration
│                        ↓                                │
│  4. 🔐 Auth Service (:8084)                            │  ← Application Services
│     📚 Library Service (:8081 + replica :8082 host)   │
│     📦 Inventory Service (:8083)                       │
│                        ↓                                │
│  5. 🚪 Gateway Service (:8080 container / :8085 host) │  ← API Entry Point
└─────────────────────────────────────────────────────────┘
```

Docker Compose handles this order automatically via readiness health checks and `depends_on: condition: service_healthy`. The gateway is only marked healthy after its required downstream services are visible in discovery.

---

## 📝 Sample API Workflow

This project already includes seeded demo data. Use the existing records instead of recreating `Clean Code` and its inventory.

```powershell
# 1. 🔑 Log in as a member
$LoginBody = @{
  email = "member@example.com"
  password = "Library123"
} | ConvertTo-Json

$USER = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/login" `
  -ContentType "application/json" `
  -Body $LoginBody).access_token

# 2. 📚 Read libraries through the gateway
Invoke-RestMethod -Uri "http://localhost:8085/api/libraries" `
  -Headers @{Authorization="Bearer $USER"}

# 3. 📖 Read the seeded demo book
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1" `
  -Headers @{Authorization="Bearer $USER"}

# 4. 🔍 Show the main cross-service flow
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" `
  -Headers @{Authorization="Bearer $USER"}

# 5. ✅ Optional pre-demo smoke check
.\scripts\demo-check.ps1
```

Continue below for the Swagger-based API walkthrough and the browser-based UI walkthrough.

---

## 🎯 Quick Reference Card

| Action | Command |
|--------|---------|
| 🚀 Start all | `docker compose up --build -d` |
| 🔄 Full reset | `docker compose down && docker compose up --build -d` |
| 💣 Nuclear reset | `docker compose down -v && docker compose up --build -d` |
| ⏹️ Stop | `docker compose down` |
| 📋 Logs | `docker compose logs -f` |
| 📊 Status | `docker compose ps` |
| 💀 Kill ports | See Kill Switch section above |

---

## Part 2. Demo API Guide

Professional Swagger-based API testing guide for the `auth-service`, `library-service`, and `inventory-service`.

## Purpose

This guide provides a structured walkthrough for demonstrating and testing the project APIs through Swagger UI. It is designed for technical walkthroughs and manual verification of the main API flows.

The recommended order is:

1. Authenticate through the Auth Service
2. Use the JWT to authorize Library Service Swagger
3. Use the same JWT to authorize Inventory Service Swagger
4. Run read operations first
5. Run write operations only if an `ADMIN` account is available

## Icon Legend

| Icon | Meaning |
|---|---|
| `🔐` | Authentication or JWT handling |
| `📚` | Library Service testing |
| `📦` | Inventory Service testing |
| `🌐` | Swagger or service URL |
| `⚠️` | Important note or common mistake |
| `✅` | Expected successful outcome |
| `🛡️` | Authorization or role requirement |

## 🌐 Swagger Endpoints

| Service | Swagger URL |
|---|---|
| Auth Service | [Auth Swagger](http://localhost:8084/swagger-ui.html) |
| Library Service | [Library Swagger](http://localhost:8081/swagger-ui.html) |
| Inventory Service | [Inventory Swagger](http://localhost:8083/swagger-ui.html) |

## 🧪 Recommended Demo Data

Use these values for a stable walkthrough.

| Field | Value |
|---|---|
| Library ID | `1` |
| Book ID | `1` |
| ISBN | `978-0-13-468599-1` |
| Book Title | `Clean Code` |
| Library Search City | `Limerick` |
| Inventory Branch ID | `TUS-MOYLISH` |

## Service API Overview

### Auth Service

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/auth/info` | Retrieve authentication metadata |
| `POST` | `/auth/signup` | Create a member account and receive a JWT |
| `POST` | `/auth/login` | Authenticate an existing member and receive a JWT |
| `POST` | `/auth/renew` | Renew an eligible JWT once |

### Library Service

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/libraries` | List all libraries |
| `GET` | `/api/libraries/{id}` | Retrieve one library |
| `GET` | `/api/libraries/search` | Search libraries by city |
| `POST` | `/api/libraries` | Create a library |
| `PUT` | `/api/libraries/{id}` | Update a library |
| `DELETE` | `/api/libraries/{id}` | Delete a library |
| `GET` | `/api/books` | List all books |
| `GET` | `/api/books/{id}` | Retrieve one book |
| `GET` | `/api/books/{id}/availability` | Retrieve a book with aggregated inventory |
| `GET` | `/api/books/isbn/{isbn}` | Retrieve a book by ISBN |
| `GET` | `/api/books/search` | Search books |
| `GET` | `/api/books/library/{libraryId}` | Retrieve books for one library |
| `POST` | `/api/books` | Create a book |
| `PUT` | `/api/books/{id}` | Update a book |
| `DELETE` | `/api/books/{id}` | Delete a book |

### Inventory Service

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/inventory/{isbn}` | Retrieve aggregated inventory by ISBN |
| `GET` | `/api/inventory/{isbn}/branches` | Retrieve inventory split by branch |
| `GET` | `/api/inventory/branch/{branchId}` | Retrieve all inventory items for a branch |
| `GET` | `/api/inventory/item/{id}` | Retrieve one inventory item |
| `POST` | `/api/inventory` | Create an inventory item |
| `PUT` | `/api/inventory/item/{id}` | Update an inventory item |
| `DELETE` | `/api/inventory/item/{id}` | Delete an inventory item |
| `POST` | `/api/inventory/{isbn}/reserve` | Reserve one copy |
| `POST` | `/api/inventory/{isbn}/return` | Return one copy |

## 🔐 Step 1: Authenticate Through the Auth Service

Open [Auth Service Swagger](http://localhost:8084/swagger-ui.html).

### 1.1 Retrieve Auth Metadata

Run `GET /auth/info`.

Purpose:

- confirm the active authentication mode
- confirm the registration role
- confirm whether OAuth providers are configured

Expected result:

- `200 OK`

### 1.2 Create a Member Account

Run `POST /auth/signup`.

> ⚠️ **Important:** After signup succeeds, copy the returned `access_token` immediately. You will need this token to authenticate Library Service Swagger and Inventory Service Swagger.

Use a fresh email address each time:

> Copy and paste this JSON into [Auth Service Swagger](http://localhost:8084/swagger-ui.html) -> `POST /auth/signup` -> `Try it out` -> `Request body`.

```json
{
  "fullName": "Swagger Demo Member",
  "email": "swagger-demo-20260327123045@library.local",
  "password": "Library123"
}
```

Expected result:

- `200 OK`
- response contains `access_token`
- role is `USER`

> ⚠️ **Important:** Keep the copied token available before moving to the next service.

Password rules:

- minimum 8 characters
- maximum 100 characters
- must contain at least one letter
- must contain at least one number

### 1.3 Log In to an Existing Account

If the signup email already exists, use `POST /auth/login` instead:

> ⚠️ **Important:** After login succeeds, copy the returned `access_token`. You will use the same token in the other Swagger pages.
>
> Copy and paste this JSON into [Auth Service Swagger](http://localhost:8084/swagger-ui.html) -> `POST /auth/login` -> `Try it out` -> `Request body`.

```json
{
  "email": "swagger-demo-20260327123045@library.local",
  "password": "Library123"
}
```

Expected result:

- `200 OK`
- response contains `access_token`

> ⚠️ **Important:** Do not continue to Library Service or Inventory Service until you have copied the token.

### 1.4 Authorize Swagger for Protected Services

After signup or login:

1. Copy the `access_token`
2. Open Library Swagger
3. Click `Authorize`
4. Paste the raw JWT token only
5. Repeat the same process in Inventory Swagger

> ⚠️ **Important:** Do not include `Bearer ` in the Swagger authorization dialog. The same copied token is used for both Library Service and Inventory Service authentication.
>
> ⚠️ **Important:** If you forget to copy the token during signup or login, return to Auth Service and get a fresh one before testing the protected APIs.

### 1.5 Renew the JWT

Optional.

Run `POST /auth/renew` only when the token is close to expiry.

Expected result:

- `200 OK` if renewal is allowed
- `409 Conflict` if the token is not eligible for renewal

## 📚 Step 2: Test the Library Service

Open [Library Service Swagger](http://localhost:8081/swagger-ui.html) and ensure it is authorized.

> ⚠️ **Important:** Library Service endpoints require the JWT copied from the Auth Service step.

### 2.1 Test Library GET Operations

#### `GET /api/libraries`

Purpose:

- confirm the service returns the seeded library list

Expected result:

- `200 OK`

#### `GET /api/libraries/{id}`

Use:

- `id = 1`

Purpose:

- confirm a single library can be retrieved by ID

Expected result:

- `200 OK`

#### `GET /api/libraries/search`

Use:

- `city = Limerick`

Purpose:

- confirm city-based search works

Expected result:

- `200 OK`
- includes `TUS Moylish Library`

### 2.2 Test Book GET Operations

#### `GET /api/books`

Purpose:

- confirm the service returns seeded books

Expected result:

- `200 OK`

#### `GET /api/books/{id}`

Use:

- `id = 1`

Purpose:

- confirm book retrieval by ID

Expected result:

- `200 OK`
- book title `Clean Code`

#### `GET /api/books/isbn/{isbn}`

Use:

- `isbn = 978-0-13-468599-1`

Purpose:

- confirm book retrieval by ISBN

Expected result:

- `200 OK`
- book title `Clean Code`

#### `GET /api/books/search`

Use:

- `q = Clean Code`

Purpose:

- confirm text search works

Expected result:

- `200 OK`
- result includes `Clean Code`

#### `GET /api/books/library/{libraryId}`

Use:

- `libraryId = 1`

Purpose:

- confirm library-specific book listing works

Expected result:

- `200 OK`

#### `GET /api/books/{id}/availability`

Use:

- `id = 1`

Purpose:

- confirm the `library-service` returns book data enriched with inventory data from `inventory-service`

Expected key values:

Compare the response with the key fields below.

```json
{
  "id": 1,
  "isbn": "978-0-13-468599-1",
  "title": "Clean Code",
  "inventory": {
    "totalCopies": 12,
    "availableCopies": 9,
    "reservedCopies": 1,
    "available": true,
    "branchCount": 3
  }
}
```

### 2.3 Test Library Write Operations

These endpoints require an `ADMIN` token.

If you only have a normal member token, the expected result is:

- `403 Forbidden`

> ⚠️ **Important:** The member account created through `/auth/signup` is a `USER` account, not an `ADMIN` account.

#### `POST /api/libraries`

> Copy and paste this JSON into [Library Service Swagger](http://localhost:8081/swagger-ui.html) -> `POST /api/libraries` -> `Try it out` -> `Request body`.

```json
{
  "name": "Swagger Demo Library",
  "address": "123 Demo Street",
  "city": "Galway",
  "country": "Ireland"
}
```

Expected with admin:

- `201 Created`

Save the returned library `id` for update and delete testing.

#### `PUT /api/libraries/{id}`

Use the `id` returned by the create call.

> Copy and paste this JSON into [Library Service Swagger](http://localhost:8081/swagger-ui.html) -> `PUT /api/libraries/{id}` -> `Try it out` -> `Request body`.

```json
{
  "name": "Swagger Demo Library Updated",
  "city": "Cork"
}
```

Expected with admin:

- `200 OK`

#### `DELETE /api/libraries/{id}`

Use the same created `id`.

Expected with admin:

- `204 No Content`

### 2.4 Test Book Write Operations

These endpoints also require an `ADMIN` token.

> ⚠️ **Important:** If you only have a member token, these endpoints should fail with `403 Forbidden`. That is expected behavior.

#### `POST /api/books`

Use a unique ISBN:

> Copy and paste this JSON into [Library Service Swagger](http://localhost:8081/swagger-ui.html) -> `POST /api/books` -> `Try it out` -> `Request body`.

```json
{
  "isbn": "978-1-99999-001-1",
  "title": "Swagger Demo Book",
  "author": "Demo Author",
  "publicationYear": 2026,
  "genre": "Technology",
  "libraryId": 1
}
```

Expected with admin:

- `201 Created`

Save the returned book `id`.

#### `PUT /api/books/{id}`

Use the `id` returned by the create call.

> Copy and paste this JSON into [Library Service Swagger](http://localhost:8081/swagger-ui.html) -> `PUT /api/books/{id}` -> `Try it out` -> `Request body`.

```json
{
  "title": "Swagger Demo Book Updated",
  "author": "Updated Author",
  "publicationYear": 2027,
  "genre": "Software",
  "libraryId": 1
}
```

Expected with admin:

- `200 OK`

#### `DELETE /api/books/{id}`

Use the same created `id`.

Expected with admin:

- `204 No Content`

## 📦 Step 3: Test the Inventory Service

Open [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) and ensure it is authorized.

> ⚠️ **Important:** Inventory Service Swagger must also be authorized with the same JWT copied from Auth Service.

### 3.1 Test Inventory GET Operations

#### `GET /api/inventory/{isbn}`

Use:

- `isbn = 978-0-13-468599-1`

Purpose:

- confirm aggregated inventory lookup works

Expected result:

- `200 OK`

#### `GET /api/inventory/{isbn}/branches`

Use:

- `isbn = 978-0-13-468599-1`

Purpose:

- confirm branch-level inventory breakdown works

Expected branch IDs:

- `TUS-MOYLISH`
- `TUS-ATHLONE`
- `DUBLIN-CITY`

#### `GET /api/inventory/branch/{branchId}`

Use:

- `branchId = TUS-MOYLISH`

Purpose:

- confirm branch-wide inventory listing works

Expected result:

- `200 OK`

Copy one returned inventory item `id`.

#### `GET /api/inventory/item/{id}`

Use the `id` returned from the branch lookup.

Purpose:

- confirm single inventory item retrieval works

Expected result:

- `200 OK`

### 3.2 Test Reserve and Return Operations

These operations work with a normal `USER` token when you call `inventory-service` directly on port `8083`.

> ⚠️ **Important:** These calls work on Inventory Service Swagger directly. They are not the same as going through the gateway policy.

#### `POST /api/inventory/{isbn}/reserve`

Use:

- `isbn = 978-0-13-468599-1`

> Copy and paste this JSON into [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) -> `POST /api/inventory/{isbn}/reserve` -> `Try it out` -> `Request body`.

```json
{
  "branchId": "TUS-MOYLISH"
}
```

Purpose:

- confirm one copy can be reserved

Expected result:

- `200 OK`
- `availableCopies` decreases by 1

#### `POST /api/inventory/{isbn}/return`

Use:

- `isbn = 978-0-13-468599-1`

> Copy and paste this JSON into [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) -> `POST /api/inventory/{isbn}/return` -> `Try it out` -> `Request body`.

```json
{
  "branchId": "TUS-MOYLISH"
}
```

Purpose:

- confirm one copy can be returned

Expected result:

- `200 OK`
- `availableCopies` increases again

### 3.3 Test Inventory Write Operations

These endpoints require an `ADMIN` token.

> ⚠️ **Important:** A member token is sufficient for reserve and return, but not for inventory create, update, or delete operations.

#### `POST /api/inventory`

Use an existing ISBN and a new branch ID:

> Copy and paste this JSON into [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) -> `POST /api/inventory` -> `Try it out` -> `Request body`.

```json
{
  "isbn": "978-0-13-468599-1",
  "branchId": "SWAGGER-DEMO",
  "branchName": "Swagger Demo Branch",
  "totalCopies": 5,
  "availableCopies": 4
}
```

Expected with admin:

- `201 Created`

Save the returned inventory item `id`.

#### `PUT /api/inventory/item/{id}`

Use the `id` returned by the create call.

> Copy and paste this JSON into [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) -> `PUT /api/inventory/item/{id}` -> `Try it out` -> `Request body`.

```json
{
  "totalCopies": 6,
  "availableCopies": 5,
  "reservedCopies": 1,
  "branchName": "Swagger Demo Branch Updated"
}
```

Expected with admin:

- `200 OK`

#### `DELETE /api/inventory/item/{id}`

Use the same created `id`.

Expected with admin:

- `204 No Content`

## 🛡️ Authorization Notes

`/auth/signup` creates `USER` accounts only.

> ⚠️ **Important:** The default Swagger walkthrough gives you a member JWT. That token is enough for read operations and direct reserve/return testing on Inventory Service, but not enough for admin write operations.

This means:

- all GET endpoints can be tested with a normal member JWT
- inventory `reserve` and `return` can be tested directly on `inventory-service` with a member JWT
- successful POST, PUT, and DELETE tests for library and inventory require a pre-provisioned `ADMIN` account

If no admin account is available, the write endpoints can still be tested to confirm that the API correctly returns `403 Forbidden` for a member token.

## ⚠️ Troubleshooting

### Swagger returns `401`

> ⚠️ **Important:** Most `401` issues during the demo happen because the JWT was not copied, was copied incorrectly, or Swagger was not authorized again after refresh.

- get a fresh token from Auth Swagger
- click `Authorize` again
- paste the raw JWT only
- do not include `Bearer `

### `POST /auth/signup` returns `409 Conflict`

Use a fresh email address or switch to `POST /auth/login`.

### Swagger says `Failed to fetch`

- confirm the relevant service is running
- refresh the page
- authorize again

---

## Part 3. Demo UI Guide

Professional browser-based demonstration guide for the Gateway Member Dashboard.

## Purpose

This guide walks through the user-facing demo of the system through the browser UI exposed by `gateway-service`. It is intended for product walkthroughs and manual verification, with a focus on the member journey rather than direct API testing.

## Icon Legend

| Icon | Meaning |
|---|---|
| `🌐` | URL or browser destination |
| `🔐` | Authentication or session behavior |
| `📚` | Library browsing |
| `🔎` | Search and availability |
| `📦` | Inventory or branch-level stock |
| `⚠️` | Important note or common mistake |
| `✅` | Expected successful outcome |

## 🌐 Quick Links

| Surface | URL |
|---|---|
| Gateway UI | [Gateway UI](http://localhost:8085/) |
| Eureka Dashboard | [Eureka Dashboard](http://localhost:8761) |
| Zipkin Tracing | [Zipkin Tracing](http://localhost:9412) |

## Demo Goal

Show that the gateway exposes a real member-facing browser UI on top of the protected microservice APIs, including:

- account creation and login
- JWT-backed browser session handling
- protected library browsing
- book search
- aggregated availability
- branch-level stock lookup

## Before You Start

Run the smoke check first:

```powershell
.\scripts\demo-check.ps1
```

If the stack was just restarted, wait 30 to 90 seconds and rerun it until the happy path passes.

Open these tabs before the walkthrough:

- [Gateway UI](http://localhost:8085/)
- [Eureka Dashboard](http://localhost:8761)
- [Zipkin Tracing](http://localhost:9412)

> ℹ️ **Default:** A fresh clone exposes the gateway UI on `http://localhost:8085/`. Override it in `.env` only if you need a different host port.

Useful pre-demo facts:

- the page is served directly by `gateway-service`
- the browser communicates only with `/auth/**` and `/api/**` through the gateway host
- the UI always shows email/password and social provider buttons
- only configured social providers are clickable

## Recommended Walkthrough

### 1. 🌐 Open the Gateway UI

Open:

[Gateway UI](http://localhost:8085/)

Point out:

- this is the single browser entry point
- no Postman, curl, or Swagger is needed for the member demo
- the page is a static UI served directly from `gateway-service`

### 2. 🔐 Show the Authentication Surface

Before signing in, explain what is visible:

- `Member Login` tab
- `Create Account` tab
- email and password form
- Google, GitHub, and Facebook buttons
- the authentication note explaining that social login still ends in the same JWT session model

Suggested line:

> “This UI supports local email/password login and can also start OAuth for Google, GitHub, or Facebook when provider credentials are configured. In every case, the user ends up with the same JWT-backed session through the gateway.”

> ⚠️ **Important:** A social login button may be visible but still disabled. That means the provider exists in the UI, but the client credentials are not configured yet.

### 3. 🔐 Create an Account

For a reliable demo, create a fresh member account first.

Use values such as:

- Full name: `UI Demo Member`
- Email: `ui-demo-<timestamp>@library.local`
- Password: `Library123`

Expected outcome:

- account creation succeeds
- the user is logged in immediately
- the dashboard view appears without needing a second login step

Implementation details worth mentioning:

- passwords must be 8 to 100 characters
- passwords must contain at least one letter
- passwords must contain at least one number
- successful signup returns a JWT immediately

> ⚠️ **Important:** Use a fresh email for each live demo signup to avoid duplicate account conflicts.

Optional alternative:

- if the account already exists, switch to `Member Login`
- if social auth is configured, use one of the enabled provider buttons

### 4. 🔐 Explain the Session Banner

After authentication, point out the session strip at the top of the dashboard:

- `Member Dashboard`
- `Signed in as`
- `Role`
- `Security mode`
- `Token expires in`
- `Log Out`
- `Extend Time` when eligible

Important behavior to explain:

- the UI stores a JWT-backed session locally
- the session survives a page refresh
- tokens are issued for 30 minutes
- the token can be extended once
- `Extend Time` appears only in the last 5 minutes
- expired sessions return the user to the auth view

Suggested line:

> “This is a browser session backed by a JWT, not a server-rendered session. The gateway validates the token, and the UI tracks the remaining lifetime visibly.”

### 5. 📚 Browse the Libraries

Scroll to the `All libraries` section.

Show:

- the protected list of libraries
- expandable library cards
- library facts such as city, address, and book count
- per-library book listings inside each expanded card

Recommended stable example:

1. Open `TUS Moylish Library`
2. Show the card expanding in place
3. Point out that the books stay inside the card rather than navigating away
4. Call out a known seeded title such as `Clean Code`

Why this matters:

- the UI is loading protected data only after authentication
- the browser is not showing raw JSON
- the gateway and downstream services are being presented as a usable member experience

### 6. 🔎 Run a Search

Go to the `Find a book` section.

Recommended search inputs:

- `Clean Code`
- `978-0-13-468599-1`

Show:

- the search input
- the results summary
- the availability cards returned for the query

Search behavior worth mentioning:

- title, author, and genre searches need at least 2 characters
- ISBN-like searches can be shorter because they are treated as numeric prefix searches

> ⚠️ **Important:** If a short text search fails, use a more precise query or search by ISBN instead.

### 7. 📦 Show Availability and Branch Stock

Open the `Clean Code` search result.

Point out:

- title
- author
- ISBN
- home library
- available versus total copies
- availability status message

Use these stable seeded values:

- home library: `TUS Moylish Library`
- total copies: `12`
- available copies: `9`
- branches: `3`

Then click `Show Branches`.

Explain:

- the UI makes an additional protected call to `/api/inventory/{isbn}/branches`
- the branch-level inventory is displayed on demand
- this is the detailed inventory view behind the aggregated availability card

Expected branch identifiers:

- `TUS-MOYLISH`
- `TUS-ATHLONE`
- `DUBLIN-CITY`

> ⚠️ **Important:** Friendly branch names may be blank in this environment. That is expected with the current seed data and is not a UI defect.

### 8. ✅ Summarize the Member Journey

At this point, the UI demo has shown:

- a member can create an account or log in
- the browser receives and keeps a JWT-backed session
- protected library data is visible only after authentication
- books can be searched through the UI
- availability is aggregated and understandable
- branch-level stock can be opened only when needed

Suggested wrap-up line:

> “This page gives a real member-facing flow on top of the gateway, auth, library, and inventory services. The backend remains fully service-based, but the user interacts with it as a coherent product.”

### 9. 🌐 Optional Technical Tie-In

If you want to connect the UI to the backend architecture, open Zipkin:

[Zipkin Tracing](http://localhost:9412)

After a recent search or availability request, explain the trace path:

```text
gateway-service -> library-service -> inventory-service
```

This is a useful bridge from the user-facing walkthrough into the technical architecture discussion.

## Suggested Presenter Talk Track

> “The member enters through the gateway UI, creates an account or logs in, receives a JWT-backed session, browses protected library data, searches for a title, and then drills into live stock availability. The experience is simple in the browser, but it is still powered by coordinated cloud-native services underneath.”

## Troubleshooting

### The UI is not reachable

Wait briefly and rerun:

```powershell
.\scripts\demo-check.ps1
```

### The page looks stale

Force refresh:

```text
Ctrl+F5
```

### Signup fails because the email already exists

Use a fresh unique email such as:

```text
ui-demo-<timestamp>@library.local
```

If you want to reuse an existing account, switch to `Member Login`.

### Search returns no results

Use a known seeded value such as:

- `Clean Code`
- `978-0-13-468599-1`

### A social login button is visible but cannot be used

That provider is not configured in `.env` yet. Use email/password instead, or configure the provider credentials first.

### Branch details show IDs instead of friendly names

That is expected with the current inventory seed data. Use the branch IDs and the copy counts as the proof point.

### `demo-check.ps1` fails only on `CONFIG-SERVER` registration

The browser happy path may still work if gateway, auth, library, and inventory are healthy. Restart `discovery-server` and `config-server` before a formal architecture walkthrough that includes centralized configuration.
