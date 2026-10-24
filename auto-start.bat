@echo off
title Remora Auto Launcher
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\auto-start.ps1"
pause
