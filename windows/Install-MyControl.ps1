# MyControl (Windows) - installation.
# Seul Google Chrome reste utilisable (sans navigation privée) ;
# tous les autres navigateurs sont fermés dès qu'ils s'ouvrent.

$ErrorActionPreference = 'Stop'

# --- Droits administrateur (relance automatique avec la fenêtre Oui/Non de Windows)
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"")
    exit
}

$Source  = Split-Path -Parent $PSCommandPath
$Dossier = Join-Path $env:ProgramData 'MyControl'
$Fichiers = @('MyControl-Detection.ps1', 'MyControl-Strategies.ps1', 'MyControl-Surveillance.ps1',
              'Desactiver-MyControl.ps1')

Write-Host ''
Write-Host '=== Installation de MyControl (Windows) ===' -ForegroundColor Cyan
Write-Host ''

# --- Chrome doit être installé, sinon il ne resterait aucun navigateur
$chromes = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
if (-not $chromes) {
    Write-Host 'Google Chrome est introuvable sur ce PC.' -ForegroundColor Yellow
    Write-Host 'Sans lui, tu n''aurais plus aucun navigateur utilisable.'
    $rep = Read-Host 'Installer MyControl quand même ? (o/n)'
    if ($rep -notmatch '^[oOyY]') { Write-Host 'Annulé.'; Read-Host 'Entrée pour fermer'; exit }
}

Write-Host 'Les autres navigateurs ouverts vont être fermés : enregistre ce qui doit l''être.' -ForegroundColor Yellow
Read-Host 'Entrée pour continuer'

# --- 1. Copie des scripts dans C:\ProgramData\MyControl (modifiable par un administrateur uniquement)
if (-not (Test-Path $Dossier)) { New-Item -ItemType Directory -Path $Dossier | Out-Null }
foreach ($f in $Fichiers) {
    Copy-Item -LiteralPath (Join-Path $Source $f) -Destination (Join-Path $Dossier $f) -Force
}
# SID universels : Administrateurs (S-1-5-32-544), SYSTEM (S-1-5-18), Utilisateurs (S-1-5-32-545)
& icacls.exe $Dossier /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-545:(OI)(CI)RX' | Out-Null
Write-Host '[1/3] Fichiers installés dans' $Dossier -ForegroundColor Green

# --- 2. Stratégies des navigateurs
. (Join-Path $Dossier 'MyControl-Strategies.ps1')
Set-MyControlStrategies | Out-Null
Write-Host '[2/3] Stratégies appliquées : Chrome sans navigation privée ni mode Invité ;' -ForegroundColor Green
Write-Host '      Edge, Brave, Chromium et Firefox sans navigation privée et sans aucun site.' -ForegroundColor Green

# --- 3. Tâche planifiée de surveillance (compte SYSTEM)
$script = Join-Path $Dossier 'MyControl-Surveillance.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""
$declencheurs = @(
    (New-ScheduledTaskTrigger -AtStartup),
    (New-ScheduledTaskTrigger -AtLogOn),
    # Relance toutes les 5 min si la surveillance s'est arrêtée (une seule instance tourne à la fois)
    (New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 5))
)
$reglages = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
$compte = New-ScheduledTaskPrincipal -UserId 'S-1-5-18' -LogonType ServiceAccount -RunLevel Highest

Register-ScheduledTask -TaskName 'MyControl' -TaskPath '\MyControl\' -Action $action `
    -Trigger $declencheurs -Settings $reglages -Principal $compte `
    -Description 'MyControl : seul Google Chrome est autorisé, sans navigation privée.' -Force | Out-Null
Start-ScheduledTask -TaskName 'MyControl' -TaskPath '\MyControl\'
Write-Host '[3/3] Surveillance démarrée (tâche planifiée \MyControl\MyControl).' -ForegroundColor Green

Write-Host ''
Write-Host 'Terminé. Il reste une chose à faire toi-même :' -ForegroundColor Cyan
Write-Host '  Paramètres > Applications > Applications par défaut > Google Chrome > « Définir par défaut »'
Write-Host '  (sinon les liens ouverts depuis d''autres applis partiraient dans Edge, qui est bloqué).'
Write-Host ''
Write-Host 'Pour vérifier : dans Chrome, ouvre chrome://policy (IncognitoModeAvailability = 1).'
Write-Host 'Journal des blocages : ' (Join-Path $Dossier 'journal.log')
Write-Host ''
Read-Host 'Entrée pour fermer'
