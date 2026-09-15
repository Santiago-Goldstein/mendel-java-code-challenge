[CmdletBinding()]
param(
    [ValidateSet(
        "start",
        "stop",
        "reset",
        "test",
        "verify",
        "logs"
    )]
    [string]$Action = "start",

    [switch]$SkipTests,

    [switch]$Force,

    [string]$TestClass = "",

    [ValidateSet(
        "api",
        "mysql",
        "all"
    )]
    [string]$Service = "api",

    [ValidateRange(1, 5000)]
    [int]$Tail = 200,

    [switch]$Follow,

    [ValidateRange(5, 1800)]
    [int]$TimeoutSeconds = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = (
    Resolve-Path (
        Join-Path $PSScriptRoot "../../../.."
    )
).Path


function Assert-Command {

    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue

    if ($null -eq $command) {
        throw "Required command '$Name' was not found on PATH."
    }
}


function Assert-DockerReady {

    Assert-Command -Name "docker"

    & docker info *> $null

    if ($LASTEXITCODE -ne 0) {
        throw "Docker is installed but the Docker daemon is not available. Start Docker Desktop and try again."
    }

    & docker compose version *> $null

    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose is not available through 'docker compose'."
    }
}


function Invoke-Compose {

    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & docker compose @Arguments

    if ($LASTEXITCODE -ne 0) {

        $renderedArguments = $Arguments -join " "

        throw "docker compose $renderedArguments failed with exit code $LASTEXITCODE."
    }
}


function Get-DotEnvValue {

    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$DefaultValue
    )

    $processValue = [Environment]::GetEnvironmentVariable(
        $Name,
        "Process"
    )

    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return $processValue
    }

    $envPath = Join-Path $RepoRoot ".env"

    if (-not (Test-Path $envPath)) {
        return $DefaultValue
    }

    $escapedName = [regex]::Escape($Name)

    $pattern = "^\s*$escapedName\s*=\s*(.*)\s*$"

    foreach ($line in Get-Content $envPath) {

        if ($line -notmatch $pattern) {
            continue
        }

        $value = $Matches[1].Trim()

        #
        # Remove matching surrounding quotes without
        # embedding problematic quote literals.
        #
        # ASCII:
        #   34 = "
        #   39 = '
        #
        if ($value.Length -ge 2) {

            $firstCharacter = [int][char]$value[0]

            $lastCharacter = [int][char]$value[
                $value.Length - 1
            ]

            $doubleQuoted = (
                $firstCharacter -eq 34 -and
                $lastCharacter -eq 34
            )

            $singleQuoted = (
                $firstCharacter -eq 39 -and
                $lastCharacter -eq 39
            )

            if ($doubleQuoted -or $singleQuoted) {

                $value = $value.Substring(
                    1,
                    $value.Length - 2
                )
            }
        }

        return $value
    }

    return $DefaultValue
}


function Get-AppPort {

    $rawPort = Get-DotEnvValue `
        -Name "APP_PORT" `
        -DefaultValue "8080"

    $port = 0

    $parsed = [int]::TryParse(
        $rawPort,
        [ref]$port
    )

    if (-not $parsed) {
        throw "APP_PORT must be an integer, but '$rawPort' was configured."
    }

    return $port
}


function Get-ApiBaseUrl {

    $port = Get-AppPort

    return "http://localhost:$port"
}


function Invoke-MavenTests {

    param(
        [string]$RequestedTestClass = ""
    )

    $wrapper = Join-Path $RepoRoot "mvnw.cmd"

    if (-not (Test-Path $wrapper)) {
        throw "Maven wrapper was not found at '$wrapper'."
    }

    if ([string]::IsNullOrWhiteSpace($RequestedTestClass)) {

        & $wrapper clean test

    } else {

        & $wrapper `
            "-Dtest=$RequestedTestClass" `
            test
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Maven tests failed with exit code $LASTEXITCODE."
    }
}


function Wait-ForComposeServiceHealthy {

    param(
        [Parameter(Mandatory = $true)]
        [string]$ServiceName,

        [Parameter(Mandatory = $true)]
        [int]$Timeout
    )

    $deadline = (Get-Date).AddSeconds(
        $Timeout
    )

    while ((Get-Date) -lt $deadline) {

        $containerId = (
            & docker compose ps -q $ServiceName 2>$null |
            Select-Object -First 1
        )

        if (-not [string]::IsNullOrWhiteSpace($containerId)) {

            $status = (
                & docker inspect `
                    --format `
                    "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" `
                    $containerId `
                    2>$null |
                Select-Object -First 1
            )

            if ($status -eq "healthy") {

                Write-Host "[OK] $ServiceName is healthy."

                return
            }

            if (
                $status -eq "unhealthy" -or
                $status -eq "exited" -or
                $status -eq "dead"
            ) {

                Write-Host "[ERROR] $ServiceName entered state '$status'."

                & docker compose logs `
                    --tail 120 `
                    $ServiceName

                throw "Service '$ServiceName' failed before becoming healthy."
            }
        }

        Start-Sleep -Seconds 2
    }

    & docker compose logs `
        --tail 120 `
        $ServiceName

    throw "Timed out waiting for service '$ServiceName' to become healthy after $Timeout seconds."
}


function Wait-ForApiHealth {

    param(
        [int]$Timeout = 60
    )

    $baseUrl = Get-ApiBaseUrl

    $healthUrl = "$baseUrl/actuator/health"

    $deadline = (Get-Date).AddSeconds(
        $Timeout
    )

    while ((Get-Date) -lt $deadline) {

        try {

            $response = Invoke-RestMethod `
                -Method Get `
                -Uri $healthUrl `
                -TimeoutSec 5

            if ($response.status -eq "UP") {

                Write-Host "[OK] API health endpoint reports UP."

                return
            }

        } catch {

            #
            # The API may still be starting.
            #
        }

        Start-Sleep -Seconds 2
    }

    throw "API health endpoint did not report UP within $Timeout seconds: $healthUrl"
}


function Invoke-ReadOnlySmokeCheck {

    $baseUrl = Get-ApiBaseUrl

    Wait-ForApiHealth `
        -Timeout 30

    $url = "$baseUrl/transactions/types/__transactions_dev_skill_missing__"

    $response = Invoke-WebRequest `
        -Method Get `
        -Uri $url `
        -UseBasicParsing `
        -TimeoutSec 10

    if ($response.StatusCode -ne 200) {
        throw "Read-only API smoke check returned HTTP $($response.StatusCode)."
    }

    Write-Host "[OK] Read-only API smoke check passed."
}


function Show-ComposeStatus {

    Write-Host ""
    Write-Host "Docker Compose status:"

    & docker compose ps

    if ($LASTEXITCODE -ne 0) {
        throw "Could not read Docker Compose status."
    }
}


function Start-TransactionsStack {

    param(
        [switch]$SkipTestsForStart
    )

    Assert-DockerReady

    if ($SkipTestsForStart) {

        Write-Host "[SKIP] Maven test suite was skipped by request."

    } else {

        Write-Host "Running Maven test suite before startup..."

        Invoke-MavenTests

        Write-Host "[OK] Maven test suite passed."
    }

    Write-Host "Building and starting MySQL + API..."

    Invoke-Compose -Arguments @(
        "up",
        "--build",
        "-d"
    )

    Wait-ForComposeServiceHealthy `
        -ServiceName "mysql" `
        -Timeout $TimeoutSeconds

    Wait-ForComposeServiceHealthy `
        -ServiceName "api" `
        -Timeout $TimeoutSeconds

    Invoke-ReadOnlySmokeCheck

    Show-ComposeStatus

    $baseUrl = Get-ApiBaseUrl

    Write-Host ""
    Write-Host "[SUCCESS] Transactions challenge is ready."
    Write-Host "API:    $baseUrl"
    Write-Host "Health: $baseUrl/actuator/health"
}


Push-Location $RepoRoot

try {

    switch ($Action) {

        "start" {

            Write-Host "=== Mendel Transactions: start ==="

            Start-TransactionsStack `
                -SkipTestsForStart:$SkipTests
        }


        "stop" {

            Write-Host "=== Mendel Transactions: stop ==="

            Assert-DockerReady

            Invoke-Compose -Arguments @(
                "down"
            )

            Write-Host "[SUCCESS] Stack stopped. MySQL volume was preserved."
        }


        "reset" {

            Write-Host "=== Mendel Transactions: reset ==="

            if (-not $Force) {

                throw "Reset deletes the persistent MySQL volume. Re-run with -Force only when data destruction is intentional."
            }

            Assert-DockerReady

            Write-Host "[WARNING] Removing containers and persistent MySQL data..."

            Invoke-Compose -Arguments @(
                "down",
                "-v",
                "--remove-orphans"
            )

            Write-Host "[OK] Persistent MySQL data removed."

            Start-TransactionsStack `
                -SkipTestsForStart:$SkipTests
        }


        "test" {

            Write-Host "=== Mendel Transactions: test ==="

            Assert-DockerReady

            Invoke-MavenTests `
                -RequestedTestClass $TestClass

            Write-Host "[SUCCESS] Requested tests passed."
        }


        "verify" {

            Write-Host "=== Mendel Transactions: verify ==="

            Assert-DockerReady

            Invoke-Compose -Arguments @(
                "config",
                "--quiet"
            )

            Write-Host "[OK] compose.yaml is valid."

            Wait-ForComposeServiceHealthy `
                -ServiceName "mysql" `
                -Timeout $TimeoutSeconds

            Wait-ForComposeServiceHealthy `
                -ServiceName "api" `
                -Timeout $TimeoutSeconds

            Invoke-ReadOnlySmokeCheck

            Show-ComposeStatus

            Write-Host "[SUCCESS] Stack verification passed."
        }


        "logs" {

            Assert-DockerReady

            $logArguments = @(
                "logs",
                "--tail",
                $Tail.ToString()
            )

            if ($Follow) {
                $logArguments += "--follow"
            }

            if ($Service -ne "all") {
                $logArguments += $Service
            }

            & docker compose @logArguments

            if ($LASTEXITCODE -ne 0) {
                throw "docker compose logs failed with exit code $LASTEXITCODE."
            }
        }
    }

} catch {

    Write-Host ""
    Write-Host "[ERROR] $($_.Exception.Message)"

    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue

    if (
        (
            $Action -eq "start" -or
            $Action -eq "reset" -or
            $Action -eq "verify"
        ) -and
        $null -ne $dockerCommand
    ) {

        Write-Host ""
        Write-Host "Recent container logs:"

        & docker compose logs `
            --tail 120 `
            mysql `
            api `
            2>$null
    }

    throw

} finally {

    Pop-Location
}