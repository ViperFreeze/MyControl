@echo off
rem Double-clic : lance Desactiver-MyControl.ps1 avec les droits administrateur.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"%~dp0Desactiver-MyControl.ps1\"'"
