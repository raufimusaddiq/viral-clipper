<#
.SYNOPSIS
    Stop all Viral Clipper services
#>

$ErrorActionPreference = "Continue"
Set-Location ($PSScriptRoot | Split-Path)

Write-Host "Stopping Viral Clipper..." -ForegroundColor Yellow
docker compose --env-file .env.docker down 2>&1 | Out-Null
Write-Host "All services stopped." -ForegroundColor Green
