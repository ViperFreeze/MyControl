# MyControl (Windows) - stratégies officielles des navigateurs.
# Chrome : navigation privée et mode Invité désactivés.
# Autres navigateurs : navigation privée désactivée ET toutes les adresses bloquées,
# pour qu'ils restent inutilisables même s'ils arrivaient à s'ouvrir.

$script:StrategiesValeurs = @(
    # Google Chrome (seul navigateur autorisé)
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Google\Chrome'; Nom = 'IncognitoModeAvailability'; Valeur = 1 }
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Google\Chrome'; Nom = 'BrowserGuestModeEnabled';   Valeur = 0 }
    # Microsoft Edge
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Microsoft\Edge'; Nom = 'InPrivateModeAvailability'; Valeur = 1 }
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Microsoft\Edge'; Nom = 'BrowserGuestModeEnabled';   Valeur = 0 }
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Microsoft\Edge'; Nom = 'StartupBoostEnabled';       Valeur = 0 }
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Microsoft\Edge'; Nom = 'BackgroundModeEnabled';     Valeur = 0 }
    # Brave
    @{ Cle = 'HKLM:\SOFTWARE\Policies\BraveSoftware\Brave'; Nom = 'IncognitoModeAvailability'; Valeur = 1 }
    @{ Cle = 'HKLM:\SOFTWARE\Policies\BraveSoftware\Brave'; Nom = 'TorDisabled';               Valeur = 1 }
    # Chromium
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Chromium'; Nom = 'IncognitoModeAvailability'; Valeur = 1 }
    # Firefox
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Mozilla\Firefox'; Nom = 'DisablePrivateBrowsing'; Valeur = 1 }
)

# Listes "toutes les adresses bloquées" (valeur "1" de la clé).
$script:StrategiesListes = @(
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Microsoft\Edge\URLBlocklist';            Valeur = '*' }
    @{ Cle = 'HKLM:\SOFTWARE\Policies\BraveSoftware\Brave\URLBlocklist';       Valeur = '*' }
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Chromium\URLBlocklist';                  Valeur = '*' }
    @{ Cle = 'HKLM:\SOFTWARE\Policies\Mozilla\Firefox\WebsiteFilter\Block';    Valeur = '<all_urls>' }
)

function Initialize-Cle([string]$Cle) {
    # Attention : New-Item -Force sur une clé existante l'effacerait, d'où le test.
    if (-not (Test-Path -LiteralPath $Cle)) { New-Item -Path $Cle -Force | Out-Null }
}

# Applique les stratégies. N'écrit que ce qui manque ou a été modifié.
# Renvoie le nombre de valeurs (ré)écrites.
function Set-MyControlStrategies {
    $ecrites = 0
    foreach ($s in $script:StrategiesValeurs) {
        Initialize-Cle $s.Cle
        $actuelle = (Get-ItemProperty -LiteralPath $s.Cle -Name $s.Nom -ErrorAction SilentlyContinue).($s.Nom)
        if ($actuelle -ne $s.Valeur) {
            New-ItemProperty -LiteralPath $s.Cle -Name $s.Nom -Value $s.Valeur -PropertyType DWord -Force | Out-Null
            $ecrites++
        }
    }
    foreach ($l in $script:StrategiesListes) {
        Initialize-Cle $l.Cle
        $actuelle = (Get-ItemProperty -LiteralPath $l.Cle -Name '1' -ErrorAction SilentlyContinue).'1'
        if ($actuelle -ne $l.Valeur) {
            New-ItemProperty -LiteralPath $l.Cle -Name '1' -Value $l.Valeur -PropertyType String -Force | Out-Null
            $ecrites++
        }
    }
    return $ecrites
}

# Retire uniquement ce que MyControl a écrit.
function Remove-MyControlStrategies {
    foreach ($s in $script:StrategiesValeurs) {
        Remove-ItemProperty -LiteralPath $s.Cle -Name $s.Nom -ErrorAction SilentlyContinue
    }
    foreach ($l in $script:StrategiesListes) {
        $actuelle = (Get-ItemProperty -LiteralPath $l.Cle -Name '1' -ErrorAction SilentlyContinue).'1'
        if ($actuelle -eq $l.Valeur) {
            Remove-ItemProperty -LiteralPath $l.Cle -Name '1' -ErrorAction SilentlyContinue
        }
    }
}
