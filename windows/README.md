# MyControl pour Windows

Sur le PC, **seul Google Chrome est autorisé**, et sans navigation privée ni mode Invité.
Tous les autres navigateurs (Edge, Firefox, Brave, Opera / Opera GX, Vivaldi, Tor, navigateurs
portables ou installés plus tard…) sont **fermés dès qu'ils s'ouvrent**.

## Télécharger

Lien direct : <https://github.com/ViperFreeze/MyControl/releases/download/windows/MyControl-Windows.zip>
(reconstruit automatiquement à chaque modification du dossier `windows/`).

## Installer

1. **Installe Google Chrome** si ce n'est pas déjà fait.
2. Décompresse le zip, puis **double-clic sur `Installer-MyControl.cmd`** → Windows demande
   l'autorisation administrateur → **Oui**.
3. Enregistre ce qui est ouvert dans d'autres navigateurs : ils vont être fermés.
4. **Mets Chrome comme navigateur par défaut** : Paramètres → Applications → Applications par défaut
   → Google Chrome → *Définir par défaut*. Sinon les liens ouverts depuis d'autres applis partiraient
   dans Edge, qui est bloqué.

Vérification : dans Chrome, ouvre `chrome://policy` → `IncognitoModeAvailability` doit valoir `1`.

## Comment ça marche

Deux protections qui se complètent :

1. **Surveillance** (tâche planifiée `\MyControl\MyControl`, compte SYSTEM, lancée au démarrage) :
   toutes les 1,5 s, tout navigateur autre que Chrome est fermé. Un navigateur est reconnu :
   - par son nom (plus de 70 navigateurs connus) ;
   - par son **moteur** : un programme qui embarque Chromium, Edge, Opera ou Gecko (Firefox) est un
     navigateur, même inconnu, renommé ou portable ;
   - par ses propriétés (description, produit, éditeur : Mozilla, Brave, Opera, Vivaldi…).

   Chrome est reconnu par sa **signature numérique Google** : une copie de Chromium appelée
   `chrome.exe` reste bloquée. Ne sont jamais bloqués : WebView2 (utilisé par Windows, Teams,
   Outlook…), Thunderbird, et les programmes de Windows.

   Si les stratégies ci-dessous sont retirées, la surveillance les remet en place dans la minute.
   Les blocages sont notés dans `C:\ProgramData\MyControl\journal.log` (et un message s'affiche à
   l'écran sur Windows Pro).

2. **Stratégies officielles des navigateurs** (celles du contrôle parental et des entreprises) :
   - Chrome : navigation privée et mode Invité désactivés ;
   - Edge, Brave, Chromium, Firefox : navigation privée désactivée **et tous les sites bloqués**,
     pour qu'ils restent inutilisables même s'ils arrivaient à s'ouvrir une seconde.

## Désactiver

Double-clic sur `Desactiver-MyControl.cmd` (aussi copié dans `C:\ProgramData\MyControl`) :
**demande → 10 minutes d'attente → code à 6 chiffres à recopier** → tout est retiré.
La demande expire au bout d'une heure.

## Limite importante

La protection résiste bien à l'impulsion, mais **pas à un compte administrateur** : un admin peut
supprimer la tâche planifiée à la main. Le vrai verrou : utilise au quotidien un **compte Windows
standard**, installe MyControl depuis un compte administrateur, et garde son mot de passe à part
(ou confie-le à quelqu'un). Un compte standard ne peut ni arrêter la surveillance, ni retirer les
stratégies, ni lancer la désactivation.

## Mise à jour depuis l'ancienne version Windows

Lance simplement `Installer-MyControl.cmd` : il remplace l'ancienne version.
