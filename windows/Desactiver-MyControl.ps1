# MyControl (Windows) - désactivation volontaire.
# Même logique que sur Android : on fait une demande, puis le code n'est donné
# qu'entre 4h et 5h du matin (et au moins 10 minutes après la demande).
# Passé 5h, il faut attendre le lendemain. Rien ne permet de raccourcir l'attente.

param([switch]$FonctionsSeulement)   # utilisé par les tests

$AttenteMinutes = 10   # délai minimal entre la demande et le code
$HeureCode      = 4    # le code n'est disponible que de 4h00 à 4h59
$ValiditeHeures = 32   # une demande couvre au moins la prochaine plage 4h-5h

# Premier moment où le code peut être affiché pour une demande faite à $Demande.
function Get-DebutCode([datetime]$Demande, [datetime]$Maintenant) {
    $tot = $Demande.AddMinutes($AttenteMinutes)
    if ($Maintenant -gt $tot) { $tot = $Maintenant }
    $plage = $tot.Date.AddHours($HeureCode)
    if ($tot -le $plage) { return $plage }
    if ($tot -lt $plage.AddHours(1)) { return $tot }
    return $plage.AddDays(1)
}

function Test-CodeDisponible([datetime]$Demande, [datetime]$Maintenant) {
    return ($Maintenant.Hour -eq $HeureCode -and ($Maintenant - $Demande).TotalMinutes -ge $AttenteMinutes)
}

if ($FonctionsSeulement) { return }

$ErrorActionPreference = 'Stop'

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"")
    exit
}

$Dossier = Join-Path $env:ProgramData 'MyControl'
$Fichier = Join-Path $Dossier 'demande.json'

function Exit-Pause([string]$Texte) {
    if ($Texte) { Write-Host $Texte }
    Write-Host ''
    Read-Host 'Entrée pour fermer'
    exit
}

function Format-Moment([datetime]$t) {
    $jour = if ($t.Date -eq (Get-Date).Date) { "aujourd'hui" }
            elseif ($t.Date -eq (Get-Date).Date.AddDays(1)) { 'demain' }
            else { 'le ' + $t.ToString('dd/MM') }
    return "$jour à $($t.ToString('HH:mm'))"
}

Write-Host ''
Write-Host '=== Désactivation de MyControl ===' -ForegroundColor Cyan
Write-Host ''

if (-not (Test-Path $Dossier)) { Exit-Pause 'MyControl n''est pas installé sur ce PC.' }

$maintenant = Get-Date
$etat = $null
if (Test-Path $Fichier) {
    try { $etat = Get-Content $Fichier -Raw | ConvertFrom-Json } catch { $etat = $null }
}
$heureDemande = $null
if ($etat) {
    $heureDemande = if ($etat.heure -is [datetime]) { $etat.heure } else { [datetime]::Parse($etat.heure, $null, 'RoundtripKind') }
    $heureDemande = $heureDemande.ToLocalTime()
    $age = ($maintenant - $heureDemande).TotalHours
    if ($age -lt 0 -or $age -gt $ValiditeHeures) { $etat = $null }   # demande expirée
}

# --- Pas de demande en cours : on en crée une
if (-not $etat) {
    @{ heure = $maintenant.ToString('o') } | ConvertTo-Json | Set-Content $Fichier -Encoding UTF8
    $debut = Get-DebutCode $maintenant $maintenant
    Exit-Pause ("Demande enregistrée. Le code ne sera donné qu'entre 4h et 5h du matin.`n" +
                "Relance ce script $(Format-Moment $debut) (avant 5h00).")
}

# --- Hors de la plage 4h-5h (ou moins de 10 min après la demande)
if (-not (Test-CodeDisponible $heureDemande $maintenant)) {
    $debut = Get-DebutCode $heureDemande $maintenant
    Exit-Pause ("Le code n'est pas encore disponible. Relance ce script $(Format-Moment $debut) " +
                "(avant 5h00, sinon il faudra attendre le lendemain).")
}

# --- Dans la plage : le code est généré maintenant (une seule fois par demande)
if (-not $etat.code) {
    $octets = New-Object byte[] 4
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($octets)
    $code = ([BitConverter]::ToUInt32($octets, 0) % 1000000).ToString('000000')
    @{ heure = $heureDemande.ToString('o'); code = $code } | ConvertTo-Json | Set-Content $Fichier -Encoding UTF8
    $etat = [pscustomobject]@{ heure = $heureDemande; code = $code }
}

Write-Host "Code de désactivation : $($etat.code)" -ForegroundColor Yellow
$saisie = Read-Host 'Recopie le code avant 5h00 pour retirer MyControl (ou Entrée pour annuler)'
if ($saisie.Trim() -ne $etat.code) {
    Exit-Pause 'Code incorrect ou annulé : MyControl reste actif.'
}
if (-not (Test-CodeDisponible $heureDemande (Get-Date))) {
    Exit-Pause 'Il est 5h passées : le code n''est plus accepté. MyControl reste actif, réessaie demain entre 4h et 5h.'
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
Remove-Item $Fichier -Force -ErrorAction SilentlyContinue

Write-Host 'MyControl est désactivé : tous les navigateurs sont de nouveau utilisables.' -ForegroundColor Green
Write-Host 'Pour le remettre, relance Installer-MyControl.cmd.'
Exit-Pause ''
