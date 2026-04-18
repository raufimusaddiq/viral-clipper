<#
.SYNOPSIS
    Run full E2E test suite (pipeline + browser)
.DESCRIPTION
    Phase 1: Python pipeline E2E (no backend needed)
    Phase 2: Playwright browser E2E (needs stack running — run scripts\start.ps1 first)
#>

param(
    [switch]$PipelineOnly,
    [switch]$BrowserOnly,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
Set-Location ($PSScriptRoot | Split-Path)

$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

$exitCode = 0

# ──────────────────────────────────────────────
# Phase 1: Python Pipeline E2E
# ──────────────────────────────────────────────
if (-not $BrowserOnly) {
    Write-Host "`n=== Phase 1: Pipeline E2E Tests ===" -ForegroundColor Cyan
    Write-Host "Testing: dependencies → transcribe → segment → score → ffmpeg" -ForegroundColor DarkGray
    Write-Host ""

    $venvPython = "ai-pipeline\.venv\Scripts\python.exe"
    if (-not (Test-Path $venvPython)) {
        $venvPython = "python"
    }

    & $venvPython scripts\e2e_pipeline_test.py -v 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n  [FAIL] Pipeline E2E tests failed" -ForegroundColor Red
        $exitCode = 1
    } else {
        Write-Host "`n  [PASS] Pipeline E2E tests passed" -ForegroundColor Green
    }
}

# ──────────────────────────────────────────────
# Phase 2: Playwright Browser E2E
# ──────────────────────────────────────────────
if (-not $PipelineOnly) {
    Write-Host "`n=== Phase 2: Browser E2E Tests ===" -ForegroundColor Cyan
    Write-Host "Testing: frontend UI → backend API → full user flow" -ForegroundColor DarkGray
    Write-Host ""

    $backendUp = $false
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -TimeoutSec 3 -ErrorAction SilentlyContinue
        if ($r.StatusCode -eq 200) { $backendUp = $true }
    } catch {}

    $frontendUp = $false
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:3000" -TimeoutSec 3 -ErrorAction SilentlyContinue
        if ($r.StatusCode -eq 200) { $frontendUp = $true }
    } catch {}

    if (-not $backendUp -or -not $frontendUp) {
        Write-Host "  [SKIP] Stack not running." -ForegroundColor Yellow
        if (-not $backendUp) { Write-Host "         Backend not reachable at :8080" -ForegroundColor Yellow }
        if (-not $frontendUp) { Write-Host "         Frontend not reachable at :3000" -ForegroundColor Yellow }
        Write-Host "         Run scripts\start.ps1 first, then re-run E2E." -ForegroundColor Yellow
    } else {
        Set-Location frontend
        npx playwright test --reporter=list 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "`n  [FAIL] Browser E2E tests failed" -ForegroundColor Red
            Write-Host "         Report: frontend\playwright-report\index.html" -ForegroundColor DarkGray
            $exitCode = 1
        } else {
            Write-Host "`n  [PASS] Browser E2E tests passed" -ForegroundColor Green
        }
        Set-Location ..
    }
}

# ──────────────────────────────────────────────
# Phase 3: Python Unit Tests
# ──────────────────────────────────────────────
if (-not $BrowserOnly) {
    Write-Host "`n=== Phase 3: Unit Tests ===" -ForegroundColor Cyan

    $venvPython = "ai-pipeline\.venv\Scripts\python.exe"
    if (-not (Test-Path $venvPython)) {
        $venvPython = "python"
    }

    & $venvPython -m pytest ai-pipeline/tests/ -q --tb=no 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Unit tests failed" -ForegroundColor Red
        $exitCode = 1
    } else {
        Write-Host "  [PASS] Unit tests passed" -ForegroundColor Green
    }
}

# ──────────────────────────────────────────────
Write-Host ""
if ($exitCode -eq 0) {
    Write-Host "=== ALL E2E TESTS PASSED ===" -ForegroundColor Green
} else {
    Write-Host "=== SOME E2E TESTS FAILED ===" -ForegroundColor Red
}
Write-Host ""
exit $exitCode
