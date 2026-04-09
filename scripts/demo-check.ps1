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
    [ValidateRange(5, 300)][int]$NoProgressTimeoutSeconds = 120,
    [ValidateRange(1, 60)][int]$PollIntervalSeconds = 3,
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"
$results = [System.Collections.Generic.List[object]]::new()
$failed = $false
$gatewayReady = $false
$eurekaReady = $false
$script:lastLiveLineLength = 0
$script:stackName = Split-Path -Leaf (Split-Path -Parent $PSScriptRoot)
$script:autoCloseSeconds = $null

$expectedDockerImages = @(
    "library-microservices/discovery-server:local",
    "library-microservices/config-server:local",
    "library-microservices/auth-service:local",
    "library-microservices/library-service:local",
    "library-microservices/inventory-service:local",
    "library-microservices/gateway-service:local"
)

$expectedDockerContainers = @(
    "mysql-library",
    "mysql-inventory",
    "mysql-auth",
    "zipkin",
    "discovery-server",
    "config-server",
    "auth-service",
    "library-service",
    "library-service-2",
    "inventory-service",
    "gateway-service"
)

try {
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [Console]::OutputEncoding = $utf8NoBom
    $OutputEncoding = $utf8NoBom
} catch {
    # Best effort only. Some hosts may ignore console encoding changes.
}

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
    if ($null -ne $script:autoCloseSeconds -and $script:autoCloseSeconds -gt 0) {
        Write-Host ""
        Write-Host ("Closing in {0} seconds..." -f $script:autoCloseSeconds) -ForegroundColor DarkGray
        Start-Sleep -Seconds $script:autoCloseSeconds
        return
    }

    if ($NoPause) {
        return
    }

    Write-Host ""

    try {
        Start-Sleep -Milliseconds 250
        while ([Console]::KeyAvailable) {
            $null = [Console]::ReadKey($true)
        }

        Write-Host "Press any key to close..." -ForegroundColor DarkGray
        $null = [Console]::ReadKey($true)
        return
    } catch {
        # Fall through to Read-Host for hosts that do not support Console.ReadKey.
    }

    try {
        Write-Host "Press Enter to close..." -ForegroundColor DarkGray
        [void](Read-Host)
        return
    } catch {
        # Fall through to cmd pause when stdin is unavailable in this host.
    }

    try {
        cmd /c pause
    } catch {
        # No further fallback is available.
    }
}

trap {
    Clear-LiveLine
    Write-Host ""
    Write-Host ("[FAIL] Unexpected error: {0}" -f $_.Exception.Message) -ForegroundColor Red
    Wait-BeforeExit
    exit 1
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
        [string]$Stage,
        [string]$Detail,
        [bool]$Display = $true
    )

    return [pscustomobject]@{
        Name = $Name
        Stage = $Stage
        Ready = $Stage -eq "Ready"
        Detail = $Detail
        Display = $Display
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
            return New-WatchState -Name $Name -Stage "Ready" -Detail "healthy"
        }

        return New-WatchState -Name $Name -Stage "Starting" -Detail "status=$($health.status)"
    } catch {
        $statusCode = Get-StatusCode -Exception $_.Exception
        $stage = if ($null -ne $statusCode) { "Starting" } else { "Pending" }
        return New-WatchState -Name $Name -Stage $stage -Detail (Get-ExceptionDetail -Exception $_.Exception)
    }
}

function Test-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Uri
    )

    try {
        $null = Invoke-RestMethod -Uri $Uri -TimeoutSec 5
        return New-WatchState -Name $Name -Stage "Ready" -Detail "reachable"
    } catch {
        $statusCode = Get-StatusCode -Exception $_.Exception
        $stage = if ($null -ne $statusCode) { "Starting" } else { "Pending" }
        return New-WatchState -Name $Name -Stage $stage -Detail (Get-ExceptionDetail -Exception $_.Exception)
    }
}

function Get-EurekaApplications {
    $response = Invoke-RestMethod -Uri "$EurekaBaseUrl/eureka/apps" -Headers @{ Accept = "application/json" } -TimeoutSec 5
    return @($response.applications.application)
}

function Test-EurekaRegistrationState {
    param(
        [object[]]$ServiceStates
    )

    $requiredReady = @(
        "Discovery Server",
        "Config Server",
        "Auth Service",
        "Library Service #1",
        "Library Service #2",
        "Inventory Service",
        "Gateway Service"
    )

    $notReady = @(
        $requiredReady | Where-Object {
            $requiredName = $_
            -not ($ServiceStates | Where-Object { $_.Name -eq $requiredName -and $_.Ready })
        }
    )

    if ($notReady.Count -gt 0) {
        return New-WatchState -Name "Eureka registrations" -Stage "Pending" -Detail ("waiting for " + $notReady[0]) -Display $false
    }

    try {
        $applications = Get-EurekaApplications
        $requiredServices = @("GATEWAY-SERVICE", "LIBRARY-SERVICE", "INVENTORY-SERVICE", "CONFIG-SERVER")
        $missingServices = @(
            $requiredServices | Where-Object {
                $serviceName = $_
                -not ($applications | Where-Object { $_.name -eq $serviceName })
            }
        )

        if ($missingServices.Count -eq 0) {
            return New-WatchState -Name "Eureka registrations" -Stage "Ready" -Detail "all required services registered" -Display $false
        }

        return New-WatchState -Name "Eureka registrations" -Stage "Starting" -Detail ("waiting for " + ($missingServices -join ", ")) -Display $false
    } catch {
        $statusCode = Get-StatusCode -Exception $_.Exception
        $stage = if ($null -ne $statusCode) { "Starting" } else { "Pending" }
        return New-WatchState -Name "Eureka registrations" -Stage $stage -Detail (Get-ExceptionDetail -Exception $_.Exception) -Display $false
    }
}

function Test-LibraryReplicaState {
    param(
        [object[]]$ServiceStates
    )

    $requiredReady = @(
        "Discovery Server",
        "Library Service #1",
        "Library Service #2"
    )

    $notReady = @(
        $requiredReady | Where-Object {
            $requiredName = $_
            -not ($ServiceStates | Where-Object { $_.Name -eq $requiredName -and $_.Ready })
        }
    )

    if ($notReady.Count -gt 0) {
        return New-WatchState -Name "Library replicas" -Stage "Pending" -Detail ("waiting for " + $notReady[0]) -Display $false
    }

    try {
        $applications = Get-EurekaApplications
        $libraryApp = $applications | Where-Object { $_.name -eq "LIBRARY-SERVICE" } | Select-Object -First 1

        if (-not $libraryApp) {
            return New-WatchState -Name "Library replicas" -Stage "Starting" -Detail "waiting for LIBRARY-SERVICE registration" -Display $false
        }

        $instanceCount = @($libraryApp.instance).Count
        if ($instanceCount -ge 2) {
            return New-WatchState -Name "Library replicas" -Stage "Ready" -Detail "$instanceCount replicas registered" -Display $false
        }

        return New-WatchState -Name "Library replicas" -Stage "Starting" -Detail "$instanceCount/2 replicas registered" -Display $false
    } catch {
        $statusCode = Get-StatusCode -Exception $_.Exception
        $stage = if ($null -ne $statusCode) { "Starting" } else { "Pending" }
        return New-WatchState -Name "Library replicas" -Stage $stage -Detail (Get-ExceptionDetail -Exception $_.Exception) -Display $false
    }
}

function Get-StackStates {
    $serviceStates = @(
        (Test-HealthEndpoint -Name "Discovery Server" -Uri "$EurekaBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Config Server" -Uri "$ConfigServerBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Auth Service" -Uri "$AuthBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Library Service #1" -Uri "$LibraryBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Library Service #2" -Uri "$LibraryReplicaBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Inventory Service" -Uri "$InventoryBaseUrl/actuator/health/readiness"),
        (Test-HealthEndpoint -Name "Gateway Service" -Uri "$GatewayBaseUrl/actuator/health/readiness"),
        (Test-HttpEndpoint -Name "Zipkin" -Uri "$ZipkinBaseUrl/api/v2/services")
    )

    return @(
        $serviceStates +
        @(
            (Test-EurekaRegistrationState -ServiceStates $serviceStates),
            (Test-LibraryReplicaState -ServiceStates $serviceStates)
        )
    )
}

function Write-LiveLine {
    param([string]$Text)

    $width = [Math]::Max($script:lastLiveLineLength, $Text.Length)
    Write-Host "`r$($Text.PadRight($width))" -NoNewline
    $script:lastLiveLineLength = $Text.Length
}

function Clear-LiveLine {
    $clearWidth = $script:lastLiveLineLength

    try {
        if ($null -ne $Host.UI -and $null -ne $Host.UI.RawUI) {
            $clearWidth = [Math]::Max($clearWidth, $Host.UI.RawUI.WindowSize.Width - 1)
        }
    } catch {
        # Fall back to tracked line length only.
    }

    if ($clearWidth -le 0) {
        return
    }

    Write-Host "`r$(' ' * $clearWidth)`r" -NoNewline
    $script:lastLiveLineLength = 0
}

function Format-Elapsed {
    param([TimeSpan]$Elapsed)

    return "{0:mm\:ss}" -f $Elapsed
}

function Format-ServiceList {
    param(
        [object[]]$States,
        [int]$MaxItems = 3
    )

    if ($null -eq $States -or $States.Count -eq 0) {
        return ""
    }

    $names = @($States | Select-Object -First $MaxItems -ExpandProperty Name)
    if ($States.Count -gt $MaxItems) {
        return ($names -join ", ") + " +" + ($States.Count - $MaxItems) + " more"
    }

    return $names -join ", "
}

function Test-DockerCliAvailable {
    return $null -ne (Get-Command docker -ErrorAction SilentlyContinue)
}

function Invoke-DockerCommandQuietly {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $previousNativeErrorPreference = $null
    $restoreNativeErrorPreference = $false

    if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
        $previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
        $PSNativeCommandUseErrorActionPreference = $false
        $restoreNativeErrorPreference = $true
    }

    try {
        & docker @Arguments 2>$null
    } finally {
        if ($restoreNativeErrorPreference) {
            $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
        }
    }
}

function Get-DockerArtifactState {
    $imagesFound = [System.Collections.Generic.List[string]]::new()
    $containersFound = [System.Collections.Generic.List[string]]::new()

    if (-not (Test-DockerCliAvailable)) {
        return [pscustomobject]@{
            DockerAvailable = $false
            ImagesFound = @()
            ContainersFound = @()
            ImagesMissing = $expectedDockerImages
            ContainersMissing = $expectedDockerContainers
        }
    }

    foreach ($image in $expectedDockerImages) {
        $imageExists = $false

        try {
            Invoke-DockerCommandQuietly -Arguments @("image", "inspect", $image) | Out-Null
            $imageExists = ($LASTEXITCODE -eq 0)
        } catch {
            $imageExists = $false
        }

        if ($imageExists) {
            $imagesFound.Add($image) | Out-Null
        }
    }

    $containerNames = @(
        Invoke-DockerCommandQuietly -Arguments @("ps", "-a", "--format", "{{.Names}}") |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )

    foreach ($container in $expectedDockerContainers) {
        if ($containerNames -contains $container) {
            $containersFound.Add($container) | Out-Null
        }
    }

    return [pscustomobject]@{
        DockerAvailable = $true
        ImagesFound = @($imagesFound)
        ContainersFound = @($containersFound)
        ImagesMissing = @($expectedDockerImages | Where-Object { $imagesFound -notcontains $_ })
        ContainersMissing = @($expectedDockerContainers | Where-Object { $containersFound -notcontains $_ })
    }
}

function Get-DockerImageDisplayName {
    param([string]$Image)

    return ($Image -split "[:/]")[-2]
}

function Wait-ForDockerArtifacts {
    param(
        [int]$TimeoutSeconds,
        [int]$NoProgressTimeoutSeconds,
        [int]$PollIntervalSeconds
    )

    if (-not (Test-DockerCliAvailable)) {
        Write-Host "[WARN] Docker CLI was not found on PATH. Skipping Docker image/container wait."
        return $true
    }

    $start = Get-Date
    $deadline = $start.AddSeconds($TimeoutSeconds)
    $lastProgressAt = $start
    $progressStartAt = $null
    $waitingMessageShown = $false
    $announcedDockerReady = @{}
    $baselineDockerState = Get-DockerArtifactState

    $existingImageNames = [System.Collections.Generic.List[string]]::new()
    foreach ($image in $baselineDockerState.ImagesFound) {
        $existingImageNames.Add((Get-DockerImageDisplayName -Image $image)) | Out-Null
        $announcedDockerReady[$image] = $true
    }

    $existingContainerNames = [System.Collections.Generic.List[string]]::new()
    foreach ($container in $baselineDockerState.ContainersFound) {
        $existingContainerNames.Add($container) | Out-Null
        $announcedDockerReady["container::$container"] = $true
    }

    if ($existingImageNames.Count -gt 0) {
        $label = if ($existingImageNames.Count -eq 1) { "Cached Image Found" } else { "Cached Images Found" }
        Write-Host ("[INFO] {0}: {1}" -f $label, (($existingImageNames | ForEach-Object { $_ }) -join ", ")) -ForegroundColor DarkGray
        $lastProgressAt = Get-Date
    }

    if ($existingContainerNames.Count -gt 0) {
        $label = if ($existingContainerNames.Count -eq 1) { "Existing Container Found" } else { "Existing Containers Found" }
        Write-Host ("[INFO] {0}: {1}" -f $label, (($existingContainerNames | ForEach-Object { $_ }) -join ", ")) -ForegroundColor DarkGray
        $lastProgressAt = Get-Date
    }

    while ((Get-Date) -lt $deadline) {
        $dockerState = Get-DockerArtifactState

        $newImageNames = [System.Collections.Generic.List[string]]::new()
        $newContainerNames = [System.Collections.Generic.List[string]]::new()

        foreach ($image in $dockerState.ImagesFound) {
            if (-not $announcedDockerReady.ContainsKey($image)) {
                $newImageNames.Add((Get-DockerImageDisplayName -Image $image)) | Out-Null
                $announcedDockerReady[$image] = $true
            }
        }

        foreach ($container in $dockerState.ContainersFound) {
            $containerKey = "container::$container"
            if (-not $announcedDockerReady.ContainsKey($containerKey)) {
                $newContainerNames.Add($container) | Out-Null
                $announcedDockerReady[$containerKey] = $true
            }
        }

        if ($newImageNames.Count -gt 0) {
            if ($null -eq $progressStartAt) {
                $progressStartAt = Get-Date
            }
            $label = if ($newImageNames.Count -eq 1) { "Image Ready" } else { "Images Ready" }
            Write-Host ("[OK] {0}: {1}" -f $label, (($newImageNames | ForEach-Object { $_ }) -join ", ")) -ForegroundColor Green
            $lastProgressAt = Get-Date
        }

        if ($newContainerNames.Count -gt 0) {
            if ($null -eq $progressStartAt) {
                $progressStartAt = Get-Date
            }
            $label = if ($newContainerNames.Count -eq 1) { "Container Ready" } else { "Containers Ready" }
            Write-Host ("[OK] {0}: {1}" -f $label, (($newContainerNames | ForEach-Object { $_ }) -join ", ")) -ForegroundColor Green
            $lastProgressAt = Get-Date
        }

        if ($dockerState.ImagesMissing.Count -eq 0 -and $dockerState.ContainersMissing.Count -eq 0) {
            if ($waitingMessageShown) {
                $elapsedStart = if ($null -ne $progressStartAt) { $progressStartAt } else { $start }
                Write-Host ("[OK] Stack Ready: {0} is ready after {1}." -f $script:stackName, (Format-Elapsed -Elapsed ((Get-Date) - $elapsedStart))) -ForegroundColor Green
            }
            return $true
        }

        if (-not $waitingMessageShown) {
            Write-Host "Waiting for Docker images and containers to be ready..."
            $waitingMessageShown = $true
        }

        if (((Get-Date) - $lastProgressAt).TotalSeconds -ge $NoProgressTimeoutSeconds) {
            Write-Host ("[FAIL] No Docker startup progress detected for {0}. Stopping early." -f (Format-Elapsed -Elapsed ((Get-Date) - $lastProgressAt))) -ForegroundColor Red
            $script:autoCloseSeconds = 5

            if ($dockerState.ImagesMissing.Count -gt 0) {
                Write-Host ("Still missing images: {0}" -f ($dockerState.ImagesMissing -join ", "))
            }

            if ($dockerState.ContainersMissing.Count -gt 0) {
                Write-Host ("Still missing containers: {0}" -f ($dockerState.ContainersMissing -join ", "))
            }

            Write-Host "Start the stack with 'docker compose up --build -d' and retry once Docker is still running."
            return $false
        }

        Start-Sleep -Seconds $PollIntervalSeconds
    }

    $dockerState = Get-DockerArtifactState
    Write-Host ("[FAIL] Timed out after {0} waiting for Docker images/containers to be created." -f (Format-Elapsed -Elapsed ((Get-Date) - $start)))
    $script:autoCloseSeconds = 5

    if ($dockerState.ImagesMissing.Count -gt 0) {
        Write-Host ("Missing images: {0}" -f ($dockerState.ImagesMissing -join ", "))
    }

    if ($dockerState.ContainersMissing.Count -gt 0) {
        Write-Host ("Missing containers: {0}" -f ($dockerState.ContainersMissing -join ", "))
    }

    Write-Host "Start the stack with 'docker compose up --build -d' and retry once Docker is still running."
    return $false
}

function Wait-ForStackHealth {
    param(
        [int]$TimeoutSeconds,
        [int]$NoProgressTimeoutSeconds,
        [int]$PollIntervalSeconds
    )

    $spinnerFrames = @([string][char]0x231B, [string][char]0x23F3)
    $spinnerIndex = 0
    $announcedReady = @{}
    $start = Get-Date
    $deadline = $start.AddSeconds($TimeoutSeconds)
    $lastProgressAt = $start
    $lastStateSignature = ""
    $nextPollAt = $start
    $states = @()

    while ((Get-Date) -lt $deadline) {
        $now = Get-Date

        if ($now -ge $nextPollAt) {
            $states = Get-StackStates
            $stateSignature = (($states | ForEach-Object { "{0}:{1}:{2}" -f $_.Name, $_.Stage, $_.Detail }) -join "|")
            if ($stateSignature -ne $lastStateSignature) {
                $lastStateSignature = $stateSignature
                $lastProgressAt = $now
            }
            $readyNames = [System.Collections.Generic.List[string]]::new()

            foreach ($state in ($states | Where-Object { $_.Display })) {
                if ($state.Stage -eq "Ready" -and -not $announcedReady.ContainsKey($state.Name)) {
                    $readyNames.Add($state.Name) | Out-Null
                    $announcedReady[$state.Name] = $true
                }
            }

            if ($readyNames.Count -gt 0) {
                Clear-LiveLine
                $label = if ($readyNames.Count -eq 1) { "Service Ready" } else { "Services Ready" }
                Write-Host ("[OK] {0}: {1}" -f $label, (Format-ServiceList -States ($states | Where-Object { $readyNames -contains $_.Name }) -MaxItems 4)) -ForegroundColor Green
            }

            if (@($states | Where-Object { $_.Ready }).Count -eq $states.Count) {
                Clear-LiveLine
                Write-Host ("[OK] Demo Stack Ready: stack is healthy after {0}. Running smoke checks..." -f (Format-Elapsed -Elapsed ((Get-Date) - $start))) -ForegroundColor Green
                return $true
            }

            $nextPollAt = $now.AddSeconds($PollIntervalSeconds)
        }

        $startingStates = @($states | Where-Object { $_.Display -and $_.Stage -eq "Starting" })
        $pendingStates = @($states | Where-Object { $_.Display -and $_.Stage -eq "Pending" })
        $hiddenStartingStates = @($states | Where-Object { -not $_.Display -and $_.Stage -eq "Starting" })
        $hiddenPendingStates = @($states | Where-Object { -not $_.Display -and $_.Stage -eq "Pending" })

        $statusText = if ($startingStates.Count -gt 0) {
            "Finalizing: " + (Format-ServiceList -States $startingStates)
        } elseif ($pendingStates.Count -gt 0) {
            "Waiting: " + (Format-ServiceList -States $pendingStates)
        } elseif ($hiddenStartingStates.Count -gt 0 -or $hiddenPendingStates.Count -gt 0) {
            "Finalizing: service discovery"
        } else {
            "Finalizing startup checks"
        }

        $line = "{0} {1}" -f `
            $spinnerFrames[$spinnerIndex % $spinnerFrames.Count], `
            $statusText

        Write-LiveLine -Text $line
        $spinnerIndex++

        if (($now - $lastProgressAt).TotalSeconds -ge $NoProgressTimeoutSeconds) {
            Clear-LiveLine
            Write-Host ("[FAIL] No stack health progress detected for {0}. Stopping early." -f (Format-Elapsed -Elapsed ($now - $lastProgressAt))) -ForegroundColor Red
            $script:autoCloseSeconds = 5
            $states |
                Select-Object `
                    @{Name = "Service"; Expression = { $_.Name } }, `
                    @{Name = "Status"; Expression = {
                        switch ($_.Stage) {
                            "Ready" { "UP" }
                            "Starting" { "STARTING" }
                            default { "WAIT" }
                        }
                    } }, `
                    @{Name = "Details"; Expression = { $_.Detail } } |
                Format-Table -AutoSize
            return $false
        }

        Start-Sleep -Milliseconds 150
    }

    if ($states.Count -eq 0) {
        $states = Get-StackStates
    }

    Clear-LiveLine
    Write-Host ("[FAIL] Timed out after {0} waiting for the stack to become healthy." -f (Format-Elapsed -Elapsed ((Get-Date) - $start))) -ForegroundColor Red
    $script:autoCloseSeconds = 5
    $states |
        Select-Object `
            @{Name = "Service"; Expression = { $_.Name } }, `
            @{Name = "Status"; Expression = {
                switch ($_.Stage) {
                    "Ready" { "UP" }
                    "Starting" { "STARTING" }
                    default { "WAIT" }
                }
            } }, `
            @{Name = "Details"; Expression = { $_.Detail } } |
        Format-Table -AutoSize

    return $false
}

Write-Host "Checking Docker images and containers..." -ForegroundColor Cyan
if (-not (Wait-ForDockerArtifacts -TimeoutSeconds $StartupTimeoutSeconds -NoProgressTimeoutSeconds $NoProgressTimeoutSeconds -PollIntervalSeconds $PollIntervalSeconds)) {
    Wait-BeforeExit
    exit 1
}

Write-Host ""
Write-Host "Watching stack startup..." -ForegroundColor Cyan
if (-not (Wait-ForStackHealth -TimeoutSeconds $StartupTimeoutSeconds -NoProgressTimeoutSeconds $NoProgressTimeoutSeconds -PollIntervalSeconds $PollIntervalSeconds)) {
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
Write-Host "[OK] Demo Check Ready: the happy-path demo is ready." -ForegroundColor Green
Wait-BeforeExit
