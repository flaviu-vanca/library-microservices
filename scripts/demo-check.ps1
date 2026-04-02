param(
    [string]$GatewayBaseUrl = "http://localhost:8085",
    [string]$LibraryBaseUrl = "http://localhost:8081",
    [string]$LibraryReplicaBaseUrl = "http://localhost:8082",
    [string]$InventoryBaseUrl = "http://localhost:8083",
    [string]$AuthBaseUrl = "http://localhost:8084",
    [string]$ConfigServerBaseUrl = "http://localhost:8888",
    [string]$EurekaBaseUrl = "http://localhost:8761",
    [string]$ZipkinBaseUrl = "http://localhost:9411",
    [string]$BookId = "1",
    [string]$Isbn = "978-0-13-468599-1",
    [string]$SmokeCheckEmail = "demo-check@library.local",
    [string]$SmokeCheckPassword = "Library123",
    [string]$SmokeCheckFullName = "Smoke Check Member",
    [ValidateRange(10, 3600)][int]$StartupTimeoutSeconds = 300,
    [ValidateRange(1, 60)][int]$PollIntervalSeconds = 3,
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"
$results = [System.Collections.Generic.List[object]]::new()
$failed = $false
$gatewayReady = $false
$eurekaReady = $false
$script:lastLiveLineLength = 0

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

function Wait-BeforeExit {
    if ($NoPause -or $Host.Name -ne "ConsoleHost") {
        return
    }

    Write-Host ""
    Write-Host "Press any key to close..." -ForegroundColor DarkGray

    try {
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    } catch {
        # If key input is unavailable, just return.
    }
}

function Get-StatusCode {
    param([System.Exception]$Exception)

    if ($null -eq $Exception) {
        return $null
    }

    if ($null -ne $Exception.Response -and $null -ne $Exception.Response.StatusCode) {
        return [int]$Exception.Response.StatusCode
    }

    return $null
}

function Get-ExceptionDetail {
    param([System.Exception]$Exception)

    $statusCode = Get-StatusCode -Exception $Exception
    if ($null -ne $statusCode) {
        return "HTTP $statusCode"
    }

    $message = "Unknown error"
    if ($null -ne $Exception -and $null -ne $Exception.Message) {
        $candidate = $Exception.Message.Trim()
        if ($candidate) {
            $message = $candidate
        }
    }
    if ($message.Length -le 120) {
        return $message
    }

    return $message.Substring(0, 117) + "..."
}

function New-WatchState {
    param(
        [string]$Name,
        [bool]$Ready,
        [string]$Detail
    )

    return [pscustomobject]@{
        Name = $Name
        Ready = $Ready
        Detail = $Detail
    }
}

function Test-HealthEndpoint {
    param(
        [string]$Name,
        [string]$Uri
    )

    try {
        $health = Invoke-RestMethod -Uri $Uri -TimeoutSec 5
        if ($health.status -eq "UP") {
            return New-WatchState -Name $Name -Ready $true -Detail "healthy"
        }

        return New-WatchState -Name $Name -Ready $false -Detail "status=$($health.status)"
    } catch {
        return New-WatchState -Name $Name -Ready $false -Detail (Get-ExceptionDetail -Exception $_.Exception)
    }
}

function Test-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Uri
    )

    try {
        $null = Invoke-RestMethod -Uri $Uri -TimeoutSec 5
        return New-WatchState -Name $Name -Ready $true -Detail "reachable"
    } catch {
        return New-WatchState -Name $Name -Ready $false -Detail (Get-ExceptionDetail -Exception $_.Exception)
    }
}

function Get-StackStates {
    return @(
        (Test-HealthEndpoint -Name "Discovery Server" -Uri "$EurekaBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Config Server" -Uri "$ConfigServerBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Auth Service" -Uri "$AuthBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Library Service #1" -Uri "$LibraryBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Library Service #2" -Uri "$LibraryReplicaBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Inventory Service" -Uri "$InventoryBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Gateway Service" -Uri "$GatewayBaseUrl/actuator/health/readiness"),
        (Test-HttpEndpoint -Name "Zipkin" -Uri "$ZipkinBaseUrl/api/v2/services")
    )
}

function Write-LiveLine {
    param([string]$Text)

    $width = [Math]::Max($script:lastLiveLineLength, $Text.Length)
    Write-Host "`r$($Text.PadRight($width))" -NoNewline
    $script:lastLiveLineLength = $Text.Length
}

function Clear-LiveLine {
    if ($script:lastLiveLineLength -le 0) {
        return
    }

    Write-Host "`r$(' ' * $script:lastLiveLineLength)`r" -NoNewline
    $script:lastLiveLineLength = 0
}

function Format-Elapsed {
    param([TimeSpan]$Elapsed)

    return "{0:mm\:ss}" -f $Elapsed
}

function Wait-ForStackHealth {
    param(
        [int]$TimeoutSeconds,
        [int]$PollIntervalSeconds
    )

    $spinnerFrames = @("|", "/", "-", "\")
    $spinnerIndex = 0
    $announcedReady = @{}
    $start = Get-Date
    $deadline = $start.AddSeconds($TimeoutSeconds)
    $nextPollAt = $start
    $states = @()

    while ((Get-Date) -lt $deadline) {
        $now = Get-Date

        if ($now -ge $nextPollAt) {
            $states = Get-StackStates

            foreach ($state in $states) {
                if ($state.Ready -and -not $announcedReady.ContainsKey($state.Name)) {
                    Clear-LiveLine
                    Write-Host ("[OK] {0} is healthy" -f $state.Name) -ForegroundColor Green
                    $announcedReady[$state.Name] = $true
                }
            }

            if (@($states | Where-Object { $_.Ready }).Count -eq $states.Count) {
                Clear-LiveLine
                Write-Host ("[OK] Stack is healthy after {0}. Running smoke checks..." -f (Format-Elapsed -Elapsed ((Get-Date) - $start))) -ForegroundColor Green
                return $true
            }

            $nextPollAt = $now.AddSeconds($PollIntervalSeconds)
        }

        $readyCount = @($states | Where-Object { $_.Ready }).Count
        $pendingStates = @($states | Where-Object { -not $_.Ready })
        $pendingNames = @($pendingStates | Select-Object -First 3 -ExpandProperty Name)
        $pendingSummary = if ($pendingNames.Count -eq 0) {
            "finalizing"
        } elseif ($pendingStates.Count -gt 3) {
            ($pendingNames -join ", ") + " +" + ($pendingStates.Count - 3) + " more"
        } else {
            $pendingNames -join ", "
        }

        $line = "[{0}] Waiting for stack health ({1}/{2}) elapsed {3} - pending: {4}" -f `
            $spinnerFrames[$spinnerIndex % $spinnerFrames.Count], `
            $readyCount, `
            $states.Count, `
            (Format-Elapsed -Elapsed ((Get-Date) - $start)), `
            $pendingSummary

        Write-LiveLine -Text $line
        $spinnerIndex++
        Start-Sleep -Milliseconds 150
    }

    if ($states.Count -eq 0) {
        $states = Get-StackStates
    }

    Clear-LiveLine
    Write-Host ("[FAIL] Timed out after {0} waiting for the stack to become healthy." -f (Format-Elapsed -Elapsed ((Get-Date) - $start))) -ForegroundColor Red
    $states |
        Select-Object `
            @{Name = "Service"; Expression = { $_.Name } }, `
            @{Name = "Status"; Expression = { if ($_.Ready) { "UP" } else { "WAIT" } } }, `
            @{Name = "Details"; Expression = { $_.Detail } } |
        Format-Table -AutoSize

    return $false
}

Write-Host "Watching stack startup..." -ForegroundColor Cyan
if (-not (Wait-ForStackHealth -TimeoutSeconds $StartupTimeoutSeconds -PollIntervalSeconds $PollIntervalSeconds)) {
    Wait-BeforeExit
    exit 1
}

Write-Host ""
Write-Host "Running pre-demo checks..." -ForegroundColor Cyan

Invoke-SafeCheck -Name "Eureka readiness" -Action {
    $health = Invoke-RestMethod -Uri "$EurekaBaseUrl/actuator/health/readiness" -TimeoutSec 5
    if ($health.status -ne "UP") {
        throw "Eureka readiness returned '$($health.status)'"
    }
    $script:eurekaReady = $true
    Add-Result -Check "Eureka readiness" -Status "PASS" -Details "Eureka is ready"
}

Invoke-SafeCheck -Name "Gateway readiness" -Action {
    $health = Invoke-RestMethod -Uri "$GatewayBaseUrl/actuator/health/readiness" -TimeoutSec 5
    if ($health.status -ne "UP") {
        throw "Gateway readiness returned '$($health.status)'"
    }
    $script:gatewayReady = $true
    Add-Result -Check "Gateway readiness" -Status "PASS" -Details "Gateway is ready"
}

Invoke-SafeCheck -Name "Smoke-check member auth" -Action {
    if (-not $script:gatewayReady) {
        Add-Result -Check "Smoke-check member auth" -Status "SKIP" -Details "Skipped because the gateway is not reachable"
        return
    }

    $loginBody = @{
        email = $SmokeCheckEmail
        password = $SmokeCheckPassword
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/auth/login" -ContentType "application/json" -Body $loginBody -TimeoutSec 5
        $script:UserToken = $response.access_token
        if (-not $script:UserToken) {
            throw "No member token returned after login"
        }
        Add-Result -Check "Smoke-check member auth" -Status "PASS" -Details "Password login is working for $SmokeCheckEmail"
        return
    } catch {
        $statusCode = Get-StatusCode -Exception $_.Exception
        if ($statusCode -ne 404) {
            throw
        }
    }

    $signupBody = @{
        fullName = $SmokeCheckFullName
        email = $SmokeCheckEmail
        password = $SmokeCheckPassword
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/auth/signup" -ContentType "application/json" -Body $signupBody -TimeoutSec 5
    $script:UserToken = $response.access_token
    if (-not $script:UserToken) {
        throw "No member token returned"
    }
    Add-Result -Check "Smoke-check member auth" -Status "PASS" -Details "Password signup is working for $SmokeCheckEmail"
}

Invoke-SafeCheck -Name "Eureka registrations" -Action {
    if (-not $script:eurekaReady) {
        Add-Result -Check "Eureka registrations" -Status "SKIP" -Details "Skipped because Eureka is not reachable"
        return
    }

    $response = Invoke-RestMethod -Uri "$EurekaBaseUrl/eureka/apps" -Headers @{ Accept = "application/json" } -TimeoutSec 5
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

    $response = Invoke-RestMethod -Uri "$EurekaBaseUrl/eureka/apps" -Headers @{ Accept = "application/json" } -TimeoutSec 5
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

    $inventory = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/inventory/$Isbn" -Headers @{ Authorization = "Bearer $script:UserToken" } -TimeoutSec 5
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

    $availability = $null
    $maxAttempts = 5
    $attemptDelaySeconds = 2

    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        try {
            $candidate = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/books/$BookId/availability" -Headers @{ Authorization = "Bearer $script:UserToken" } -TimeoutSec 5
            if ($candidate.id -ne [int]$BookId) {
                throw "Book ID mismatch in availability response"
            }
            if (-not $candidate.inventory) {
                throw "No inventory object returned"
            }
            if ($candidate.inventory.totalCopies -le 0) {
                throw "Inventory fallback or empty inventory returned"
            }

            $availability = $candidate
            break
        } catch {
            if ($attempt -eq $maxAttempts) {
                throw
            }

            Start-Sleep -Seconds $attemptDelaySeconds
        }
    }

    if ($null -eq $availability) {
        throw "Availability flow did not return a valid response"
    }

    Add-Result -Check "Availability flow" -Status "PASS" -Details "Book $BookId returned live inventory (totalCopies=$($availability.inventory.totalCopies))"
}

Invoke-SafeCheck -Name "Circuit breaker actuator" -Action {
    if (-not $script:UserToken) {
        Add-Result -Check "Circuit breaker actuator" -Status "SKIP" -Details "Skipped because a USER token is not available"
        return
    }

    $circuitBreakers = Invoke-RestMethod -Uri "$LibraryBaseUrl/actuator/circuitbreakers" -Headers @{ Authorization = "Bearer $script:UserToken" } -TimeoutSec 5
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
    Wait-BeforeExit
    exit 1
}

Write-Host ""
Write-Host "Pre-demo check passed. The happy-path demo is ready." -ForegroundColor Green
Wait-BeforeExit
