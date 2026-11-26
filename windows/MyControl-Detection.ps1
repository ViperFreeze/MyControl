# MyControl (Windows) - reconnaissance des navigateurs.
# Liste blanche : seul Google Chrome (signé par Google LLC) est autorisé.
# Tout autre navigateur - connu, inconnu ou portable - est considéré comme interdit.
# Cette liste est figée dans le script, comme NAVIGATEURS_AUTORISES sur Android.

# Noms d'exécutables de navigateurs connus (sans .exe, en minuscules).
$script:NavigateursConnus = @(
    'msedge', 'iexplore', 'microsoftedge',
    'firefox', 'librewolf', 'waterfox', 'floorp', 'zen', 'palemoon', 'basilisk',
    'seamonkey', 'mullvadbrowser', 'tor', 'torbrowser', 'icecat',
    'brave', 'opera', 'opera_gx', 'operagx', 'vivaldi', 'chromium', 'thorium',
    'cromite', 'supermium', 'iron', 'slimjet', 'cent', 'centbrowser',
    'yandex', 'browser', 'whale', 'maxthon', 'ucbrowser', 'coccoc',
    'avastbrowser', 'avgbrowser', 'ccleanerbrowser', 'nortonbrowser', 'securebrowser',
    'epic', 'arc', 'comet', 'sidekick', 'wavebox', 'ghostbrowser', 'duckduckgo',
    'min', 'qutebrowser', 'falkon', 'midori', 'otter-browser', 'k-meleon',
    'lunascape', 'slimbrowser', 'polarity', 'safari', 'ladybird',
    '360chrome', '360se', 'qqbrowser', 'sogouexplorer', 'liebao',
    'blisk', 'naver', 'sputnik', 'aloha', 'kiwi'
)

# Jamais bloqués, même s'ils ressemblent à un navigateur.
# WebView2 sert à Windows, Teams, Outlook... ; Thunderbird est un client mail.
$script:Exceptions = @('msedgewebview2', 'thunderbird', 'betterbird')

# Fichiers présents à côté de l'exécutable (ou dans un sous-dossier de version)
# qui trahissent un moteur de navigateur complet : Chromium, Edge, Opera, Gecko...
$script:FichiersMoteur = @('chrome.dll', 'msedge.dll', 'opera_browser.dll', 'browser.dll',
                           'vivaldi.dll', 'brave.dll', 'xul.dll')

# Marques et mots-clés lus dans les propriétés du fichier (description, produit, éditeur).
$script:RegexMarques = '(?i)(firefox|chromium|\bopera\b|\bbrave\b|vivaldi|librewolf|waterfox|floorp|' +
    'tor browser|mullvad browser|pale moon|yandex|maxthon|coc coc|cốc cốc|naver whale|thorium|' +
    'microsoft edge|mozilla corporation|brave software|opera norway|vivaldi technologies|' +
    'web browser|internet browser|navigateur web|navigateur internet|navegador)'
$script:RegexPasNavigateur = '(?i)(webview2|thunderbird|edge update|\bupdater?\b)'

$script:CacheVerdicts = @{}

function Test-SigneParGoogle([string]$Chemin) {
    try {
        $sig = Get-AuthenticodeSignature -FilePath $Chemin
        return ($sig.Status -eq 'Valid' -and $sig.SignerCertificate.Subject -match 'O=Google LLC')
    } catch { return $false }
}

function Test-MoteurNavigateur([string]$Dossier) {
    foreach ($f in $script:FichiersMoteur) {
        if (Test-Path -LiteralPath (Join-Path $Dossier $f)) { return $true }
    }
    # Chrome, Brave, Vivaldi, Opera rangent leur moteur dans un sous-dossier "numéro de version".
    $sousDossiers = Get-ChildItem -LiteralPath $Dossier -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^\d+(\.\d+)+$' } | Select-Object -First 5
    foreach ($d in $sousDossiers) {
        foreach ($f in $script:FichiersMoteur) {
            if (Test-Path -LiteralPath (Join-Path $d.FullName $f)) { return $true }
        }
    }
    return $false
}

# Renvoie $true si le programme est un navigateur interdit.
function Get-VerdictNavigateur([string]$Chemin, [string]$Nom) {
    $nom = $Nom.ToLowerInvariant()
    if ($script:Exceptions -contains $nom) { return $false }

    $windir = $env:windir.TrimEnd('\') + '\'
    $dansWindows = $Chemin.StartsWith($windir, [StringComparison]::OrdinalIgnoreCase)

    $ressemble = $script:NavigateursConnus -contains $nom
    $info = $null
    if (-not $ressemble) {
        try { $info = (Get-Item -LiteralPath $Chemin -ErrorAction Stop).VersionInfo } catch { }
        if ($info) {
            $texte = "$($info.FileDescription) | $($info.ProductName) | $($info.CompanyName)"
            if ($texte -match $script:RegexPasNavigateur) { return $false }
            if ($texte -match $script:RegexMarques) { $ressemble = $true }
        }
    }
    if (-not $ressemble -and -not $dansWindows) {
        $ressemble = Test-MoteurNavigateur (Split-Path -Parent $Chemin)
    }
    if (-not $ressemble) { return $false }

    # Liste blanche : tout ce qui est signé par Google (Chrome et ses processus annexes).
    if (Test-SigneParGoogle $Chemin) { return $false }
    return $true
}

function Test-NavigateurInterdit([string]$Chemin, [string]$Nom) {
    if ([string]::IsNullOrEmpty($Chemin)) { return $false }
    if ($script:CacheVerdicts.ContainsKey($Chemin)) { return $script:CacheVerdicts[$Chemin] }
    $v = [bool](Get-VerdictNavigateur $Chemin $Nom)
    $script:CacheVerdicts[$Chemin] = $v
    return $v
}
