param(
    [string]$GatewayBaseUrl = "http://localhost:8085",
    [string]$LibraryBaseUrl = "http://localhost:8081",
    [string]$EurekaBaseUrl = "http://localhost:8761",
    [string]$BookId = "1",
    [string]$Isbn = "978-0-13-468599-1"
)

$ErrorActionPreference = "Stop"
$results = [System.Collections.Generic.List[object]]::new()
$failed = $false
$gatewayReady = $false
$eurekaReady = $false

function Add-Result {
    param(
        [string]$Check,
        [string]$Status,
        [string]$Details
    )

    $results.Add([pscustomobject]@{
        Check = $Check
        Status = $Status
        Details = $Details
    }) | Out-Null
}

function Invoke-SafeCheck {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    try {
        & $Action
    } catch {
        $script:failed = $true
        Add-Result -Check $Name -Status "FAIL" -Details $_.Exception.Message
    }
}

Write-Host "Running pre-demo checks..." -ForegroundColor Cyan

Invoke-SafeCheck -Name "Eureka readiness" -Action {
    $health = Invoke-RestMethod -Uri "$EurekaBaseUrl/actuator/health/readiness"
    if ($health.status -ne "UP") {
        throw "Eureka readiness returned '$($health.status)'"
    }
    $script:eurekaReady = $true
    Add-Result -Check "Eureka readiness" -Status "PASS" -Details "Eureka is ready"
}

Invoke-SafeCheck -Name "Gateway readiness" -Action {
    $health = Invoke-RestMethod -Uri "$GatewayBaseUrl/actuator/health/readiness"
    if ($health.status -ne "UP") {
        throw "Gateway readiness returned '$($health.status)'"
    }
    $script:gatewayReady = $true
    Add-Result -Check "Gateway readiness" -Status "PASS" -Details "Gateway is ready"
}

Invoke-SafeCheck -Name "Create smoke-check member" -Action {
    if (-not $script:gatewayReady) {
        Add-Result -Check "Create smoke-check member" -Status "SKIP" -Details "Skipped because the gateway is not reachable"
        return
    }
    $email = "demo-check+$([guid]::NewGuid().ToString('N').Substring(0, 12))@library.local"
    $body = @{
        fullName = "Smoke Check Member"
        email = $email
        password = "Library123"
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/auth/signup" -ContentType "application/json" -Body $body
    $script:UserToken = $response.access_token
    if (-not $script:UserToken) {
        throw "No member token returned"
    }
    Add-Result -Check "Create smoke-check member" -Status "PASS" -Details "Password signup is working for $email"
}

Invoke-SafeCheck -Name "Eureka registrations" -Action {
    if (-not $script:eurekaReady) {
        Add-Result -Check "Eureka registrations" -Status "SKIP" -Details "Skipped because Eureka is not reachable"
        return
    }
    $response = Invoke-RestMethod -Uri "$EurekaBaseUrl/eureka/apps" -Headers @{Accept = "application/json"}
    $applications = @($response.applications.application)
    foreach ($service in @("GATEWAY-SERVICE", "LIBRARY-SERVICE", "INVENTORY-SERVICE", "CONFIG-SERVER")) {
        if (-not ($applications | Where-Object { $_.name -eq $service })) {
            throw "Missing registration for $service"
        }
    }
    Add-Result -Check "Eureka registrations" -Status "PASS" -Details "All expected services are registered"
}

Invoke-SafeCheck -Name "Library-service replicas" -Action {
    if (-not $script:eurekaReady) {
        Add-Result -Check "Library-service replicas" -Status "SKIP" -Details "Skipped because Eureka is not reachable"
        return
    }
    $response = Invoke-RestMethod -Uri "$EurekaBaseUrl/eureka/apps" -Headers @{Accept = "application/json"}
    $applications = @($response.applications.application)
    $libraryApp = $applications | Where-Object { $_.name -eq "LIBRARY-SERVICE" } | Select-Object -First 1
    if (-not $libraryApp) {
        throw "LIBRARY-SERVICE is not registered"
    }
    $instanceCount = @($libraryApp.instance).Count
    if ($instanceCount -lt 2) {
        throw "Expected at least 2 LIBRARY-SERVICE instances, found $instanceCount"
    }
    Add-Result -Check "Library-service replicas" -Status "PASS" -Details "Eureka shows $instanceCount LIBRARY-SERVICE instances"
}

Invoke-SafeCheck -Name "Gateway inventory route" -Action {
    if (-not $script:UserToken) {
        Add-Result -Check "Gateway inventory route" -Status "SKIP" -Details "Skipped because a USER token is not available"
        return
    }
    $inventory = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/inventory/$Isbn" -Headers @{Authorization = "Bearer $script:UserToken"}
    if ($inventory.isbn -ne $Isbn) {
        throw "Inventory ISBN mismatch"
    }
    Add-Result -Check "Gateway inventory route" -Status "PASS" -Details "Inventory returned for $Isbn"
}

Invoke-SafeCheck -Name "Availability flow" -Action {
    if (-not $script:UserToken) {
        Add-Result -Check "Availability flow" -Status "SKIP" -Details "Skipped because a USER token is not available"
        return
    }
    $availability = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/books/$BookId/availability" -Headers @{Authorization = "Bearer $script:UserToken"}
    if ($availability.id -ne [int]$BookId) {
        throw "Book ID mismatch in availability response"
    }
    if (-not $availability.inventory) {
        throw "No inventory object returned"
    }
    if ($availability.inventory.totalCopies -le 0) {
        throw "Inventory fallback or empty inventory returned"
    }
    Add-Result -Check "Availability flow" -Status "PASS" -Details "Book $BookId returned live inventory (totalCopies=$($availability.inventory.totalCopies))"
}

Invoke-SafeCheck -Name "Circuit breaker actuator" -Action {
    if (-not $script:UserToken) {
        Add-Result -Check "Circuit breaker actuator" -Status "SKIP" -Details "Skipped because a USER token is not available"
        return
    }
    $circuitBreakers = Invoke-RestMethod -Uri "$LibraryBaseUrl/actuator/circuitbreakers" -Headers @{Authorization = "Bearer $script:UserToken"}
    $payload = $circuitBreakers | ConvertTo-Json -Depth 5 -Compress
    if ($payload -notmatch "inventoryService") {
        throw "inventoryService circuit breaker not found"
    }
    Add-Result -Check "Circuit breaker actuator" -Status "PASS" -Details "inventoryService circuit breaker is exposed"
}

$results | Format-Table -AutoSize

if ($failed) {
    Write-Host ""
    Write-Host "Pre-demo check failed. Fix the failing items before presenting." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Pre-demo check passed. The happy-path demo is ready." -ForegroundColor Green
