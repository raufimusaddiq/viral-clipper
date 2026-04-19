<#
.SYNOPSIS
    Start Viral Clipper full stack (one command)
.DESCRIPTION
    Builds Docker images if needed, then starts backend + frontend.
    Backend (Spring Boot + Python AI) runs in Docker with GPU.
    Frontend (Next.js) runs in Docker.
    Waits for health checks, then prints URLs.
#>

param(
    [switch]$Build,
    [switch]$FrontendOnly,
    [switch]$BackendOnly
)

$ErrorActionPreference = "Continue"
$ProjectRoot = $PSScriptRoot | Split-Path

$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

Set-Location $ProjectRoot

if (-not (Test-Path "data")) { New-Item -ItemType Directory -Path "data" -Force | Out-Null }
foreach ($sub in @("raw","audio","transcripts","segments","renders","exports","variations","analytics","logs")) {
    $dir = Join-Path "data" $sub
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
}

Write-Host ""
Write-Host "=== Viral Clipper Startup ===" -ForegroundColor Cyan

if (-not $FrontendOnly) {
    Write-Host "[1/3] Checking Docker images..." -ForegroundColor Yellow
    $backendImage = docker images --format "{{.Repository}}:{{.Tag}}" 2>$null | Select-String "viralvideo-backend"
    
    if ($Build -or -not $backendImage) {
        Write-Host "       Building backend image (this may take a few minutes)..." -ForegroundColor Yellow
        $buildResult = docker compose --env-file .env.docker build backend 2>&1
        $buildExit = $LASTEXITCODE
        $errors = $buildResult | Where-Object { $_ -match "ERROR|FAILED" -and $_ -notmatch "WARN" }
        if ($errors) {
            $errors | ForEach-Object { Write-Host "       $_" -ForegroundColor Red }
        }
        if ($buildExit -ne 0) {
            Write-Host "       Docker build FAILED (exit code $buildExit). Check Dockerfile and .env.docker" -ForegroundColor Red
            Write-Host "       Try: docker compose --env-file .env.docker build --no-cache backend" -ForegroundColor Yellow
            exit 1
        }
        Write-Host "       Backend image built." -ForegroundColor Green
    } else {
        Write-Host "       Backend image exists. Use -Build to rebuild." -ForegroundColor Green
    }
}

if (-not $BackendOnly) {
    $frontendImage = docker images --format "{{.Repository}}:{{.Tag}}" 2>$null | Select-String "viralvideo-frontend"
    if ($Build -or -not $frontendImage) {
        Write-Host "       Building frontend image..." -ForegroundColor Yellow
        $buildResult = docker compose --env-file .env.docker build frontend 2>&1
        $buildExit = $LASTEXITCODE
        $errors = $buildResult | Where-Object { $_ -match "ERROR|FAILED" -and $_ -notmatch "WARN" }
        if ($errors) {
            $errors | ForEach-Object { Write-Host "       $_" -ForegroundColor Red }
        }
        if ($buildExit -ne 0) {
            Write-Host "       Frontend build FAILED (exit code $buildExit)." -ForegroundColor Red
            exit 1
        }
        Write-Host "       Frontend image built." -ForegroundColor Green
    }
}

Write-Host "[2/3] Starting services..." -ForegroundColor Yellow
$services = @()
if (-not $FrontendOnly) { $services += "backend" }
if (-not $BackendOnly) { $services += "frontend" }

$upResult = docker compose --env-file .env.docker up -d @services 2>&1
$upExit = $LASTEXITCODE
if ($upExit -ne 0) {
    Write-Host "       Docker compose up FAILED (exit code $upExit):" -ForegroundColor Red
    Write-Host "       $upResult" -ForegroundColor DarkGray
    exit 1
}

Write-Host "[3/3] Waiting for services..." -ForegroundColor Yellow

if (-not $FrontendOnly) {
    Write-Host "       Backend: waiting for health check..." -NoNewline
    $retries = 30
    $healthy = $false
    while ($retries -gt 0) {
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($r.StatusCode -eq 200) { $healthy = $true; break }
        } catch {}
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
        $retries--
    }
    if ($healthy) {
        Write-Host " OK" -ForegroundColor Green
    } else {
        Write-Host " TIMEOUT" -ForegroundColor Red
        Write-Host "       Check: docker compose --env-file .env.docker logs backend" -ForegroundColor Yellow
    }
}

if (-not $BackendOnly) {
    Write-Host "       Frontend: waiting for response..." -NoNewline
    $retries = 20
    $ready = $false
    while ($retries -gt 0) {
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:3000" -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($r.StatusCode -eq 200) { $ready = $true; break }
        } catch {}
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
        $retries--
    }
    if ($ready) {
        Write-Host " OK" -ForegroundColor Green
    } else {
        Write-Host " TIMEOUT" -ForegroundColor Red
        Write-Host "       Check: docker compose --env-file .env.docker logs frontend" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Pruning old images ===" -ForegroundColor Yellow
docker image prune -f 2>$null | Out-Null
foreach ($svc in @("viralvideo-backend", "viralvideo-frontend")) {
    $ids = docker images --format "{{.ID}}" --filter "dangling=true" --filter "reference=${svc}" 2>$null
    if ($ids) { $ids | ForEach-Object { docker rmi $_ 2>$null | Out-Null } }
}
$reclaimed = docker system df --format "{{.Reclaimable}}" 2>$null
Write-Host "       Done." -ForegroundColor Green

Write-Host ""
Write-Host "=== Services Running ===" -ForegroundColor Green
if (-not $FrontendOnly) { Write-Host "  Backend:  http://localhost:8080" -ForegroundColor Cyan }
if (-not $BackendOnly)  { Write-Host "  Frontend: http://localhost:3000" -ForegroundColor Cyan }
Write-Host "  API Docs: http://localhost:8080/api/health" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  Stop:     scripts\stop.ps1" -ForegroundColor DarkGray
Write-Host "  Logs:     docker compose --env-file .env.docker logs -f" -ForegroundColor DarkGray
Write-Host "  E2E Test: scripts\e2e-test.ps1" -ForegroundColor DarkGray
Write-Host ""
