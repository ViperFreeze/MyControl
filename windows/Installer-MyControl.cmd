@echo off
rem Double-clic : lance Install-MyControl.ps1 avec les droits administrateur.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"%~dp0Install-MyControl.ps1\"'"
