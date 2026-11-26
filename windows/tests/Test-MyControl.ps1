# Tests lancés par GitHub Actions sur un vrai Windows (Chrome, Edge et Firefox y sont installés).
$ErrorActionPreference = 'Stop'
$racine = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
. (Join-Path $racine 'MyControl-Detection.ps1')
. (Join-Path $racine 'MyControl-Strategies.ps1')
$echecs = 0

function Test-Cas([string]$Chemin, [bool]$AttenduInterdit) {
    if (-not (Test-Path -LiteralPath $Chemin)) { Write-Host "ABSENT   $Chemin"; return }
    $nom = [IO.Path]::GetFileNameWithoutExtension($Chemin)
    $v = Test-NavigateurInterdit $Chemin $nom
    $ok = ($v -eq $AttenduInterdit)
    $etiquette = if ($v) { 'bloqué ' } else { 'autorisé' }
    Write-Host ("{0}  {1}  {2}" -f $(if ($ok) { 'OK  ' } else { 'ECHEC' }), $etiquette, $Chemin)
    if (-not $ok) { $script:echecs++ }
}

$pf = $env:ProgramFiles; $pf86 = ${env:ProgramFiles(x86)}
# Navigateur autorisé
Test-Cas "$pf\Google\Chrome\Application\chrome.exe" $false
# Navigateurs interdits
Test-Cas "$pf86\Microsoft\Edge\Application\msedge.exe" $true
Test-Cas "$pf\Microsoft\Edge\Application\msedge.exe" $true
Test-Cas "$pf\Mozilla Firefox\firefox.exe" $true
# Chromium « portable » renommé : une copie de Chrome non signée doit être bloquée
$faux = Join-Path $env:RUNNER_TEMP 'portable'
New-Item -ItemType Directory -Force $faux | Out-Null
Copy-Item "$env:windir\System32\notepad.exe" (Join-Path $faux 'navigo.exe')
New-Item -ItemType File -Force (Join-Path $faux '120.0.1.2\chrome.dll') | Out-Null
Test-Cas (Join-Path $faux 'navigo.exe') $true
# Programmes ordinaires
Test-Cas "$env:windir\System32\notepad.exe" $false
Test-Cas "$env:windir\explorer.exe" $false
Test-Cas (Get-Command powershell.exe).Source $false
Test-Cas (Get-Command git.exe).Source $false
Get-ChildItem "$pf86\Microsoft\EdgeWebView\Application" -Recurse -Filter msedgewebview2.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1 | ForEach-Object { Test-Cas $_.FullName $false }
Get-ChildItem "$pf86\Microsoft\EdgeUpdate" -Filter MicrosoftEdgeUpdate.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1 | ForEach-Object { Test-Cas $_.FullName $false }

# Stratégies : écriture, lecture, retrait
$n = Set-MyControlStrategies
Write-Host "Stratégies écrites : $n"
if ((Get-ItemProperty 'HKLM:\SOFTWARE\Policies\Google\Chrome').IncognitoModeAvailability -ne 1) { Write-Host 'ECHEC Chrome'; $echecs++ }
if ((Get-ItemProperty 'HKLM:\SOFTWARE\Policies\Microsoft\Edge\URLBlocklist').'1' -ne '*') { Write-Host 'ECHEC Edge'; $echecs++ }
if ((Test-Path 'HKLM:\SOFTWARE\Policies\Google\Chrome\URLBlocklist')) { Write-Host 'ECHEC Chrome bloqué !'; $echecs++ }
if ((Set-MyControlStrategies) -ne 0) { Write-Host 'ECHEC : réécriture inutile'; $echecs++ }
Remove-MyControlStrategies
if ((Get-ItemProperty 'HKLM:\SOFTWARE\Policies\Google\Chrome' -ErrorAction SilentlyContinue).IncognitoModeAvailability) { Write-Host 'ECHEC retrait'; $echecs++ }

if ($echecs) { Write-Host "$echecs échec(s)"; exit 1 }
Write-Host 'Tous les tests passent.'
