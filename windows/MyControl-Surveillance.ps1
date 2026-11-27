# MyControl (Windows) - surveillance.
# Lancé par la tâche planifiée \MyControl\MyControl (compte SYSTEM, au démarrage).
# Ferme tout navigateur autre que Chrome dès qu'il s'ouvre, et remet en place
# les stratégies des navigateurs si elles ont été retirées.

$ErrorActionPreference = 'SilentlyContinue'
$Dossier = Join-Path $env:ProgramData 'MyControl'
$Journal = Join-Path $Dossier 'journal.log'

# Une seule instance à la fois (la tâche relance le script toutes les 5 min s'il s'est arrêté).
$creee = $false
$verrou = New-Object System.Threading.Mutex($true, 'Global\MyControlSurveillance', [ref]$creee)
if (-not $creee) { exit 0 }

. (Join-Path $Dossier 'MyControl-Detection.ps1')
. (Join-Path $Dossier 'MyControl-Strategies.ps1')

function Write-Journal([string]$Texte) {
    try {
        if ((Test-Path $Journal) -and (Get-Item $Journal).Length -gt 1MB) {
            Move-Item $Journal "$Journal.old" -Force
        }
        Add-Content -Path $Journal -Value ("{0:yyyy-MM-dd HH:mm:ss}  {1}" -f (Get-Date), $Texte) -Encoding UTF8
    } catch { }
}

# Petit message à l'écran (msg.exe n'existe pas sur Windows Famille : dans ce cas, rien).
$Msg = Join-Path $env:windir 'System32\msg.exe'
$DernierMessage = @{}
function Send-Avertissement([string]$Nom) {
    if (-not (Test-Path $Msg)) { return }
    $maintenant = Get-Date
    if ($DernierMessage.ContainsKey($Nom) -and ($maintenant - $DernierMessage[$Nom]).TotalSeconds -lt 60) { return }
    $DernierMessage[$Nom] = $maintenant
    Start-Process -FilePath $Msg -ArgumentList '*', '/TIME:8', "MyControl : $Nom est bloqué. Seul Google Chrome est autorisé." -WindowStyle Hidden
}

Write-Journal 'Surveillance démarrée'
$prochaineVerifStrategies = Get-Date

while ($true) {
    foreach ($p in Get-Process) {
        if ($p.Id -le 4) { continue }
        $chemin = $p.Path
        if (-not $chemin) { continue }
        if (Test-NavigateurInterdit $chemin $p.ProcessName) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            Write-Journal "Bloqué : $($p.ProcessName)  ($chemin)"
            Send-Avertissement $p.ProcessName
        }
    }

    if ((Get-Date) -ge $prochaineVerifStrategies) {
        $n = Set-MyControlStrategies
        if ($n -gt 0) { Write-Journal "Stratégies remises en place ($n valeur(s))" }
        $prochaineVerifStrategies = (Get-Date).AddMinutes(1)
    }

    Start-Sleep -Milliseconds 1500
}
