#!/usr/bin/env pwsh
# PowerShell pre-commit hook for Git

Write-Host "Running ktlint check..." -ForegroundColor Cyan
& ./gradlew.bat ktlintCheck --daemon

if ($LASTEXITCODE -ne 0) {
    Write-Host "ktlint check failed, please fix the above issues before committing" -ForegroundColor Red
    exit 1
}

Write-Host "Running detekt..." -ForegroundColor Cyan
& ./gradlew.bat detekt --daemon

if ($LASTEXITCODE -ne 0) {
    Write-Host "detekt check failed, please fix the above issues before committing" -ForegroundColor Red
    exit 1
}

Write-Host "All checks passed!" -ForegroundColor Green
exit 0
