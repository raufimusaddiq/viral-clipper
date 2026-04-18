<#
.SYNOPSIS
    Verify all dependencies for Viral Clipper
.DESCRIPTION
    Checks Docker, GPU passthrough, Node.js, Python venv, ffmpeg, yt-dlp
    Returns exit code 0 if all OK, 1 if missing deps
#>

$ErrorActionPreference = "SilentlyContinue"

$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

function Check($name, $cmd, $args) {
    $result = Get-Command $cmd -ErrorAction SilentlyContinue
    if ($result) {
        Write-Host "  [OK] $name" -ForegroundColor Green
        return $true
    } else {
        Write-Host "  [MISSING] $name ($cmd not found)" -ForegroundColor Red
        return $false
    }
}

function CheckDockerGPU {
    Write-Host "  Checking Docker GPU passthrough..." -NoNewline
    $result = docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host " OK" -ForegroundColor Green
        return $true
    } else {
        Write-Host " FAILED" -ForegroundColor Red
        Write-Host "    GPU not available in Docker. Install NVIDIA Container Toolkit." -ForegroundColor Yellow
        return $false
    }
}

Write-Host "`n=== Viral Clipper Environment Check ===" -ForegroundColor Cyan
Write-Host ""

$allOk = $true

Write-Host "--- Core Dependencies ---" -ForegroundColor Yellow
$allOk = $allOk -and (Check "Docker" "docker")
$allOk = $allOk -and (Check "Node.js" "node")
$allOk = $allOk -and (Check "npm" "npm")
$allOk = $allOk -and (Check "Python" "python")
$allOk = $allOk -and (Check "ffmpeg" "ffmpeg")
$allOk = $allOk -and (Check "yt-dlp" "yt-dlp")

Write-Host ""
Write-Host "--- Python AI Pipeline ---" -ForegroundColor Yellow
if (Test-Path "ai-pipeline\.venv\Scripts\python.exe") {
    Write-Host "  [OK] Python venv" -ForegroundColor Green
    $whisper = & "ai-pipeline\.venv\Scripts\python.exe" -c "import faster_whisper; print('ok')" 2>&1
    if ($whisper -match "ok") {
        Write-Host "  [OK] faster-whisper" -ForegroundColor Green
    } else {
        Write-Host "  [MISSING] faster-whisper in venv" -ForegroundColor Red
        $allOk = $false
    }
    $cv2 = & "ai-pipeline\.venv\Scripts\python.exe" -c "import cv2; print('ok')" 2>&1
    if ($cv2 -match "ok") {
        Write-Host "  [OK] opencv" -ForegroundColor Green
    } else {
        Write-Host "  [MISSING] opencv in venv" -ForegroundColor Red
        $allOk = $false
    }
} else {
    Write-Host "  [MISSING] Python venv (run: python -m venv ai-pipeline\.venv)" -ForegroundColor Red
    $allOk = $false
}

Write-Host ""
Write-Host "--- Docker GPU ---" -ForegroundColor Yellow
$allOk = $allOk -and (CheckDockerGPU)

Write-Host ""
Write-Host "--- Project Files ---" -ForegroundColor Yellow
$files = @(
    @("docker-compose.yml", "Docker Compose config"),
    @(".env.docker", "Docker env vars"),
    @("backend\Dockerfile", "Backend Dockerfile"),
    @("frontend\Dockerfile", "Frontend Dockerfile"),
    @("backend\src\main\resources\schema.sql", "Database schema"),
    @("ai-pipeline\transcribe.py", "Transcribe script"),
    @("ai-pipeline\segment.py", "Segment script"),
    @("ai-pipeline\score.py", "Score script")
)
foreach ($f in $files) {
    if (Test-Path $f[0]) {
        Write-Host "  [OK] $($f[1])" -ForegroundColor Green
    } else {
        Write-Host "  [MISSING] $($f[1]) ($($f[0]))" -ForegroundColor Red
        $allOk = $false
    }
}

Write-Host ""
Write-Host "--- Unit Tests ---" -ForegroundColor Yellow
$pytestResult = & python -m pytest ai-pipeline/tests/ -q --tb=no 2>&1
if ($LASTEXITCODE -eq 0) {
    $line = ($pytestResult | Select-String "passed").Line
    Write-Host "  [OK] Python tests: $line" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Python tests failing" -ForegroundColor Red
    $allOk = $false
}

Write-Host ""
if ($allOk) {
    Write-Host "=== ALL CHECKS PASSED ===" -ForegroundColor Green
    Write-Host "Run: scripts\start.ps1" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host "=== SOME CHECKS FAILED ===" -ForegroundColor Red
    Write-Host "Fix the missing items above, then re-run this script." -ForegroundColor Yellow
    exit 1
}
