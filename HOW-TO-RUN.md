# 📚 How to Run the Application

## ✅ Prerequisites

| Requirement | Version | Verify Command |
|-------------|---------|----------------|
| 🐳 Docker Desktop | Latest | `docker --version` |

> ⚠️ **Important:** Docker Desktop must be **running** before executing any commands.

> ℹ️ **Default gateway URL:** A fresh clone exposes the gateway on `http://localhost:8085`.
> Copy `.env.example` to `.env` only if you want to override ports or add social login credentials.
> The repository now includes `.env.dockerhub` configured for the `flaviuvanca` Docker Hub namespace. Update only the tag if you publish a different version.

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

### ▶️ Start Everything From Published Docker Hub Images

```powershell
docker compose --env-file .env.dockerhub -f docker-compose.dockerhub.yml up -d
```

Use this after the images have been published to Docker Hub. The stack is now self-contained, so people only need `.env.dockerhub` and `docker-compose.dockerhub.yml`; they do not need the rest of the repository contents.

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

## 📦 Publish Application Images to Docker Hub

### 1. Log In to Docker Hub

```powershell
docker login
```

### 2. Build and Push the Published Images

```powershell
.\scripts\publish-docker-images.ps1 -DockerHubNamespace flaviuvanca -ImageTag v1.0.0
```

This script builds and pushes:

- `discovery-server`
- `config-server`
- `auth-service`
- `library-service`
- `inventory-service`
- `gateway-service`
- `mysql-library`
- `mysql-inventory`
- `mysql-auth`

It also generates `.env.dockerhub` at the repository root so the pull-only stack knows which Docker Hub namespace and tag to use.

### 3. Give Consumers the One-Command Start

```powershell
docker compose --env-file .env.dockerhub -f docker-compose.dockerhub.yml up -d
```

The Docker Hub compose file uses your published service images, your published seeded MySQL images, and upstream `openzipkin/zipkin:latest`.

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

```
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

For a fuller walkthrough, use [`DEMO-API-GUIDE.md`](./DEMO-API-GUIDE.md) and [`DEMO-UI-GUIDE.md`](./DEMO-UI-GUIDE.md).

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
