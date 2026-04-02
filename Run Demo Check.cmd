@echo off
setlocal
cd /d "%~dp0"

title Library Demo Check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo-check.ps1" -NoPause

echo.
pause
