@echo off
echo Installing Git hooks...

:: Create pre-commit hook that calls our PowerShell script
echo @echo off > "%~dp0..\.git\hooks\pre-commit"
echo powershell.exe -ExecutionPolicy Bypass -NoProfile -File "%~dp0pre-commit.ps1" >> "%~dp0..\.git\hooks\pre-commit"

echo Git hooks installed successfully!
