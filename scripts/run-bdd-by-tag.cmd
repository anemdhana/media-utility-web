@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%run-bdd-by-tag.ps1"

if not exist "%PS_SCRIPT%" (
    echo PowerShell helper not found: "%PS_SCRIPT%"
    exit /b 1
)

powershell -ExecutionPolicy Bypass -File "%PS_SCRIPT%" %*
exit /b %ERRORLEVEL%
