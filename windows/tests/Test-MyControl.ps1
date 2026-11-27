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
# X et Telegram (appli de bureau) : bloqués en entier
$tg = Join-Path $env:RUNNER_TEMP 'Telegram Desktop'
New-Item -ItemType Directory -Force $tg | Out-Null
Copy-Item "$env:windir\System32\notepad.exe" (Join-Path $tg 'Telegram.exe')
Test-Cas (Join-Path $tg 'Telegram.exe') $true
Copy-Item "$env:windir\System32\notepad.exe" (Join-Path $tg 'Unigram.exe')
Test-Cas (Join-Path $tg 'Unigram.exe') $true
# Applis d'IA : autorisées ; navigateurs IA (moteur Chromium embarqué) : bloqués
$ia = Join-Path $env:RUNNER_TEMP 'IA'
New-Item -ItemType Directory -Force $ia | Out-Null
foreach ($n in 'Claude', 'ChatGPT', 'Copilot', 'Perplexity', 'Cursor', 'ollama app') {
    Copy-Item "$env:windir\System32\notepad.exe" (Join-Path $ia "$n.exe")
    Test-Cas (Join-Path $ia "$n.exe") $false
}
$comet = Join-Path $env:RUNNER_TEMP 'Comet'
New-Item -ItemType Directory -Force $comet | Out-Null
Copy-Item "$env:windir\System32\notepad.exe" (Join-Path $comet 'perplexity.exe')
New-Item -ItemType File -Force (Join-Path $comet '140.0.1.0\chrome.dll') | Out-Null
Test-Cas (Join-Path $comet 'perplexity.exe') $true
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
$listeChrome = (Get-ItemProperty 'HKLM:\SOFTWARE\Policies\Google\Chrome\URLBlocklist').PSObject.Properties |
    Where-Object { $_.Name -match '^\d+$' } | ForEach-Object { $_.Value }
Write-Host "Chrome bloque : $($listeChrome -join ', ')"
if ($listeChrome -contains '*') { Write-Host 'ECHEC Chrome entièrement bloqué !'; $echecs++ }
if (-not ($listeChrome -contains 'x.com' -and $listeChrome -contains 'telegram.org')) { Write-Host 'ECHEC X/Telegram non bloqués dans Chrome'; $echecs++ }
if ((Set-MyControlStrategies) -ne 0) { Write-Host 'ECHEC : réécriture inutile'; $echecs++ }
Remove-MyControlStrategies
if ((Get-ItemProperty 'HKLM:\SOFTWARE\Policies\Google\Chrome' -ErrorAction SilentlyContinue).IncognitoModeAvailability) { Write-Host 'ECHEC retrait'; $echecs++ }
if ((Get-ItemProperty 'HKLM:\SOFTWARE\Policies\Google\Chrome\URLBlocklist' -ErrorAction SilentlyContinue).'1') { Write-Host 'ECHEC retrait liste Chrome'; $echecs++ }

# Désactivation : code seulement entre 4h et 5h, au moins 10 min après la demande
. (Join-Path $racine 'Desactiver-MyControl.ps1') -FonctionsSeulement
function D([string]$s) { [datetime]::ParseExact($s, 'yyyy-MM-dd HH:mm', $null) }
$casCode = @(
    @('2026-11-27 22:00', '2026-11-27 22:05', '2026-11-28 04:00'),   # le soir -> 4h le lendemain
    @('2026-11-28 02:00', '2026-11-28 02:30', '2026-11-28 04:00'),   # la nuit -> 4h le jour même
    @('2026-11-28 04:20', '2026-11-28 04:21', '2026-11-28 04:30'),   # pendant la plage -> +10 min
    @('2026-11-28 04:55', '2026-11-28 04:56', '2026-11-29 04:00'),   # trop tard -> lendemain
    @('2026-11-28 10:00', '2026-11-28 10:00', '2026-11-29 04:00')    # matin -> lendemain
)
foreach ($c in $casCode) {
    $obtenu = Get-DebutCode (D $c[0]) (D $c[1])
    $ok = $obtenu -eq (D $c[2])
    Write-Host ("{0}  demande {1} -> code {2:yyyy-MM-dd HH:mm}" -f $(if ($ok) { 'OK  ' } else { 'ECHEC' }), $c[0], $obtenu)
    if (-not $ok) { $echecs++ }
}
if (Test-CodeDisponible (D '2026-11-28 04:00') (D '2026-11-28 05:00')) { Write-Host 'ECHEC 5h00 acceptée'; $echecs++ }
if (Test-CodeDisponible (D '2026-11-28 04:00') (D '2026-11-28 04:05')) { Write-Host 'ECHEC moins de 10 min'; $echecs++ }
if (-not (Test-CodeDisponible (D '2026-11-27 23:00') (D '2026-11-28 04:59'))) { Write-Host 'ECHEC 4h59 refusée'; $echecs++ }

if ($echecs) { Write-Host "$echecs échec(s)"; exit 1 }
Write-Host 'Tous les tests passent.'
