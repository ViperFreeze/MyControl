# MyControl (Windows) - désactivation volontaire.
# Même logique que sur Android : on demande, on attend 10 minutes, on recopie un code,
# et seulement ensuite le blocage est retiré. Rien ne permet de raccourcir l'attente.

$ErrorActionPreference = 'Stop'

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"")
    exit
}

$Dossier = Join-Path $env:ProgramData 'MyControl'
$Demande = Join-Path $Dossier 'demande.json'
$AttenteMinutes  = 10
$ValiditeMinutes = 60   # passé ce délai après la demande, il faut recommencer

function Exit-Pause([string]$Texte) {
    if ($Texte) { Write-Host $Texte }
    Write-Host ''
    Read-Host 'Entrée pour fermer'
    exit
}

Write-Host ''
Write-Host '=== Désactivation de MyControl ===' -ForegroundColor Cyan
Write-Host ''

if (-not (Test-Path $Dossier)) { Exit-Pause 'MyControl n''est pas installé sur ce PC.' }

$maintenant = Get-Date
$etat = $null
if (Test-Path $Demande) {
    try { $etat = Get-Content $Demande -Raw | ConvertFrom-Json } catch { $etat = $null }
}
if ($etat) {
    $heure = if ($etat.heure -is [datetime]) { $etat.heure } else { [datetime]::Parse($etat.heure, $null, 'RoundtripKind') }
    $depuis = ($maintenant - $heure.ToLocalTime()).TotalMinutes
    if ($depuis -lt 0 -or $depuis -gt $ValiditeMinutes) { $etat = $null }   # demande expirée
}

# --- Pas de demande en cours : on en crée une
if (-not $etat) {
    $octets = New-Object byte[] 4
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($octets)
    $code = ([BitConverter]::ToUInt32($octets, 0) % 1000000).ToString('000000')
    @{ heure = $maintenant.ToString('o'); code = $code } | ConvertTo-Json | Set-Content $Demande -Encoding UTF8
    Exit-Pause ("Demande enregistrée. Relance ce script dans $AttenteMinutes minutes pour obtenir le code " +
                "(il reste valable jusqu'à $($maintenant.AddMinutes($ValiditeMinutes).ToString('HH:mm'))).")
}

# --- Demande en cours, mais attente pas terminée
$restant = $AttenteMinutes - $depuis
if ($restant -gt 0) {
    Exit-Pause ("Encore {0} min {1:00} s d'attente avant d'obtenir le code." -f [math]::Floor($restant),
                [math]::Floor(($restant % 1) * 60))
}

# --- Attente terminée : code à recopier
Write-Host "Code de désactivation : $($etat.code)" -ForegroundColor Yellow
$saisie = Read-Host 'Recopie le code pour retirer MyControl (ou Entrée pour annuler)'
if ($saisie.Trim() -ne $etat.code) {
    Remove-Item $Demande -Force
    Exit-Pause 'Code incorrect ou annulé : MyControl reste actif. La demande est effacée.'
}

# --- Retrait
Unregister-ScheduledTask -TaskName 'MyControl' -TaskPath '\MyControl\' -Confirm:$false -ErrorAction SilentlyContinue
Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" |
    Where-Object { $_.CommandLine -like '*MyControl-Surveillance.ps1*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

. (Join-Path $Dossier 'MyControl-Strategies.ps1')
Remove-MyControlStrategies

# Le dossier est conservé avec le journal ; les scripts sont retirés.
Get-ChildItem $Dossier -Filter '*.ps1' | Remove-Item -Force -ErrorAction SilentlyContinue
Remove-Item $Demande -Force -ErrorAction SilentlyContinue

Write-Host 'MyControl est désactivé : tous les navigateurs sont de nouveau utilisables.' -ForegroundColor Green
Write-Host 'Pour le remettre, relance Installer-MyControl.cmd.'
Exit-Pause ''
