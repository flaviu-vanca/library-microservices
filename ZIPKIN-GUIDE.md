# Zipkin Guide

This document explains how to use Zipkin with this application in a practical way.

It covers:

- what Zipkin is doing in this app
- how tracing is configured here
- which requests are worth tracing
- how to generate traces
- how to search and read them in the UI
- how to use Zipkin in the screencast
- how to troubleshoot common issues

## 1. What Zipkin Is In This Project

Zipkin is the distributed tracing UI for this microservices system.

In simple terms:

- a **trace** is one end-to-end request
- a **span** is one step inside that request

So if one client request goes through three services, you normally expect:

- one trace
- several spans inside that trace

For this project, the most important distributed request path is:

```text
client -> gateway-service -> library-service -> inventory-service
```

That means Zipkin is useful because it shows:

- that the request entered through the gateway
- that the library service handled business logic
- that the library service called the inventory service
- how long each hop took
- where the request failed or slowed down

## 2. Why Zipkin Matters For This App

The assignment is not only about proving that the services are running.
It is also about proving that they behave as a distributed system at runtime.

Zipkin gives you visual proof of that.

Without Zipkin:

- you would know the request worked
- but you would not clearly see which services participated

With Zipkin:

- you can show the actual request path across services
- you can explain the difference between gateway routing and service-to-service communication
- you can diagnose latency or failure more intelligently

## 3. Current Tracing Setup In This Repo

This project is already configured to send traces to Zipkin.

### Zipkin container

In [docker-compose.yml](K:\Semester 2\Microservices Architecture\Assignment #2\library-microservices-cloud-native\docker-compose.yml#L74), Zipkin is run as:

- service name: `zipkin`
- image: `openzipkin/zipkin:3.6.0`
- host port: `9411`

The image is pinned on purpose so a new upstream Zipkin release does not silently break the local demo environment.

So the UI is available at:

```text
http://localhost:9411
```

### Shared tracing configuration

In [application.yml](K:\Semester 2\Microservices Architecture\Assignment #2\library-microservices-cloud-native\config-repo\application.yml#L39), tracing is configured centrally:

- sampling probability is `1.0`
- Zipkin endpoint is `http://localhost:9411/api/v2/spans` for host-based local runs
- log pattern includes `traceId` and `spanId`

That means:

- every request should be sampled
- the services are already pointing to Zipkin
- logs and traces can be correlated

When the stack runs through Docker Compose, the containers override the exporter with `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans`. The host-facing UI stays on `http://localhost:9411`.

### Important implementation note for the library hop

The `library-service -> inventory-service` call now uses a load-balanced `WebClient.Builder` that still applies Boot `WebClientCustomizer`s. That detail matters because it preserves Micrometer observation and trace propagation on the outbound hop instead of creating a separate root trace.

### Services that currently matter most in Zipkin

For this app, the main services to look for are:

- `gateway-service`
- `library-service`
- `inventory-service`
- `auth-service` for signup/login flows

The current verified happy path produces a single trace containing:

- `gateway-service`
- `library-service`
- `inventory-service`

## 4. Mental Model Before You Start

Use this mental model while reading traces:

### The gateway is the external entry point

Client requests come in through:

```text
http://localhost:8085
```

### The library service is Service A

It owns books and libraries.

### The inventory service is Service B

It owns stock and branch availability.

### The best cross-service request

This request is the best one to trace:

```text
GET /api/books/1/availability
```

because it is not a fake demo hop.
It is a real business flow where the library service calls the inventory service to build the response.

## 5. Before Using Zipkin

Make sure the app is running:

```powershell
docker compose ps
```

For the cleanest demo, you can still inspect `docker compose ps`, but the smoke check now waits for the stack automatically and prints live readiness progress as each service comes online.

If you want to confirm the gateway by hand as well:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/actuator/health/readiness"
```

The gateway readiness payload should report `requiredServices` as `UP`.

If the stack is not running:

```powershell
docker compose up --build -d
```

Open Zipkin:

```text
http://localhost:9411
```

You can also run the existing smoke check:

```powershell
.\scripts\demo-check.ps1
```

What it does now:

- shows a spinner while the stack is still starting
- prints `[OK]` as Discovery, Config, Auth, both Library replicas, Inventory, Gateway, and Zipkin become healthy
- only starts the happy-path checks after the stack is ready
- reuses `demo-check@library.local` by default, so reruns do not create endless throwaway users

Optional tuning:

```powershell
.\scripts\demo-check.ps1 -StartupTimeoutSeconds 420 -PollIntervalSeconds 2
```

## 6. Important Note About Zipkin Storage

In this compose file, Zipkin is running with its default setup.
There is no external database configured for Zipkin storage.

That means traces should be treated as temporary.

Practical consequence:

- traces are fine for development and the assignment demo
- they are not intended as long-term durable trace storage
- if the Zipkin container is recreated, old traces may disappear

For this coursework, that is completely fine.

## 7. The Best Requests To Trace In This App

### Best request overall

Use this request:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" -Headers @{Authorization="Bearer $TOKEN"}
```

Why this is the best:

- enters through the gateway
- hits the library service
- causes a call to the inventory service
- gives a clear multi-service trace

### Good secondary request

You can also trace signup:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/signup" -ContentType "application/json" -Body $SignupBody
```

Why it is useful:

- it shows the gateway plus auth-service flow

### Less useful requests

These are not wrong, but they are weaker Zipkin demos:

- `GET /`
- `GET /actuator/health`
- direct UI asset loads

Reason:

- they often hit only one service
- they do not show the main distributed behaviour the assignment wants

## 8. Step-By-Step: Generate A Good Trace

### Step 1: Create a token

```powershell
$Email = "zipkin-$(Get-Date -Format yyyyMMddHHmmss)@library.local"
$SignupBody = @{
  fullName = "Zipkin Demo Member"
  email = $Email
  password = "Library123"
} | ConvertTo-Json

$TOKEN = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/auth/signup" `
  -ContentType "application/json" `
  -Body $SignupBody).access_token
```

Why:

- the availability endpoint is protected
- you need a JWT to make the request through the gateway

### Step 2: Trigger the main distributed request

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" -Headers @{Authorization="Bearer $TOKEN"}
```

Expected meaning:

- gateway accepts the request
- gateway routes to library-service
- library-service calls inventory-service
- spans should be exported to Zipkin

### Step 3: Open Zipkin

Go to:

```text
http://localhost:9411
```

### Step 4: Query recent traces

In the UI:

1. Use `gateway-service` as the initial `Service Name`
2. Set a recent lookback such as `15 minutes`
3. Keep the end time as current time
4. Click `Run Query`

If you do not see anything:

- try `library-service`
- refresh the page
- run the request again

### Step 5: Open the newest trace

Open the most recent result.

This is the trace you will interpret.

## 9. What You Should Expect To See

For the `GET /api/books/1/availability` request, you should expect a trace with spans involving:

- `gateway-service`
- `library-service`
- `inventory-service`

Conceptually, it should look like:

```text
gateway-service
  -> library-service
     -> inventory-service
```

This tells you:

- the gateway was the entry point
- the library service handled the request logic
- the inventory service participated downstream

That is exactly the evidence you want for the assignment.

## 10. How To Read The Zipkin UI

When you open a trace, do not try to read everything at once.
Focus on these questions:

1. Which service received the request first?
2. Which downstream services were called?
3. Which part took the longest?
4. Did the downstream call happen successfully?

### A. Service name

This tells you which service created the span.

Examples in this app:

- `gateway-service`
- `library-service`
- `inventory-service`

### B. Span hierarchy

This shows parent-child relationships between steps.

In this app:

```text
gateway-service
  -> library-service
     -> inventory-service
```

means:

- the gateway received the client request
- the library service handled the routed API call
- the inventory service was called from the library service

### C. Duration

Span duration helps you find the slow part.

For example:

- if the inventory span is much slower than the gateway span, the downstream dependency is the likely cause of latency
- if the gateway is fast but the overall trace is slow, you look deeper into child spans

### D. Missing downstream span

If you expected `inventory-service` but do not see it:

- the request may not have crossed services
- the downstream service may have been unavailable
- the trace may show only the part of the request that completed

That is already useful information.

## 11. A Concrete Example For This App

Suppose you run:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" -Headers @{Authorization="Bearer $TOKEN"}
```

Then in Zipkin you open the newest trace.

A good interpretation is:

> This trace shows that the request entered through the gateway, then the library service handled the availability endpoint, and then the inventory service was called to supply stock information. That proves the request path crossed service boundaries at runtime.

That is all you need to say.

## 12. How To Use Zipkin In The Screencast

Use this exact workflow:

1. Make the availability request
2. Open Zipkin
3. Search recent traces
4. Open the newest trace
5. Point to the services involved
6. Explain why tracing matters in distributed systems

Suggested wording:

> Here in Zipkin I can follow a single request across service boundaries. This request entered through the gateway, was handled by the library service, and then called the inventory service. That gives runtime evidence that the application behaves as a distributed system.

Then add:

> This matters because in distributed systems, I cannot rely on one service log alone. Tracing lets me see where a request went and where latency or failure occurred.

## 13. Using Zipkin With Logs

This project also writes `traceId` and `spanId` into logs via the shared log pattern in [application.yml](K:\Semester 2\Microservices Architecture\Assignment #2\library-microservices-cloud-native\config-repo\application.yml#L47).

That gives you log-trace correlation.

Workflow:

1. find a trace in Zipkin
2. note the trace ID
3. inspect logs for the relevant service
4. search for the same trace ID

Useful commands:

```powershell
docker compose logs gateway-service --tail 100
docker compose logs library-service --tail 100
docker compose logs inventory-service --tail 100
```

If you want to narrow logs by trace ID in PowerShell:

```powershell
docker compose logs library-service | Select-String "YOUR_TRACE_ID"
```

This is useful when you want both:

- the visual request path from Zipkin
- the exact log lines from one service

## 14. Failure Scenario With Zipkin

Zipkin is also useful in the resilience section of the assignment.

### Step 1: Stop inventory-service

```powershell
docker compose stop inventory-service
```

### Step 2: Trigger the same request again

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" -Headers @{Authorization="Bearer $TOKEN"}
```

### Step 3: Check Zipkin

What you may see:

- `gateway-service` span still exists
- `library-service` span still exists
- `inventory-service` span may be missing or may show a failed downstream attempt depending on timing

How to explain that:

> The request still entered through the gateway and reached the library service. The failure occurred in the downstream dependency path. Zipkin helps show where the request stopped progressing normally.

That works well together with your resilience explanation.

## 15. Optional Advanced Use: Zipkin API

The UI is enough for the assignment, but if you want more control, Zipkin also exposes APIs.

### Query recent traces

Example:

```powershell
curl.exe "http://localhost:9411/api/v2/traces?serviceName=gateway-service&limit=10"
```

### Query dependency graph

Example:

```powershell
curl.exe "http://localhost:9411/api/v2/dependencies?endTs=9999999999999&lookback=86400000"
```

This is optional.
The UI is simpler for the screencast.

## 16. Troubleshooting

### Problem: Zipkin UI does not open

Check:

```powershell
docker compose ps
```

Make sure the `zipkin` container is running.

Also confirm the host port is still `9411`.

### Problem: Zipkin opens but no traces appear

Try this sequence:

1. run the traced request again
2. query by `gateway-service`
3. if nothing appears, query by `library-service`
4. increase lookback to `15 minutes`
5. refresh the page

Because sampling is `1.0`, you should normally get traces if the request was actually processed.

### Problem: I only see one service in the trace

Likely reasons:

- the endpoint did not cross services
- the request failed before the downstream call
- you used a request that only hit one service

Use this request instead:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" -Headers @{Authorization="Bearer $TOKEN"}
```

### Problem: I do not understand the trace

Reduce it to these three questions:

1. Which service got the request first?
2. Which downstream service was called next?
3. Which span took the longest?

That is enough to use Zipkin effectively in this app.

### Problem: Traces disappeared

This Zipkin setup is for development/demo use.

Possible reasons:

- Zipkin container restarted
- traces aged out of temporary storage
- you are searching too far back or not far enough

Regenerate a fresh trace and query again.

## 17. Minimal Workflow

If you want the shortest working process:

1. Open `http://localhost:9411`
2. Create a token
3. Run:

```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/books/1/availability" -Headers @{Authorization="Bearer $TOKEN"}
```

4. In Zipkin, search `gateway-service`
5. Open the newest trace
6. Show the path:

```text
gateway-service -> library-service -> inventory-service
```

That is enough to use Zipkin correctly for this application.
