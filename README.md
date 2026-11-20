# MyControl

Appli Android qui bloque la navigation privée (Incognito, InPrivate, Mode secret…) dans les principaux navigateurs.

## Fonctionnement

Un **service d'accessibilité** surveille uniquement les navigateurs listés :

1. **Clic intercepté** : un appui sur « Nouvel onglet de navigation privée », « Incognito », « InPrivate », « Mode secret »… déclenche immédiatement un *Retour*.
2. **Écran détecté** : si un onglet privé s'affiche quand même, l'appli renvoie à l'écran d'accueil.

Aucune donnée n'est stockée (à part un compteur de blocages) ni envoyée.

## Protection anti-désactivation

La protection se **verrouille** d'un bouton. Une fois verrouillée, elle ne peut plus être levée facilement : la seule sortie est de **désinstaller** l'appli, et pour ça il faut un code qui n'est disponible **qu'entre 4h et 5h du matin**.

- Tant que la protection est verrouillée, le service **bloque les écrans** permettant d'arrêter MyControl : page du service d'accessibilité, infos de l'appli (Forcer l'arrêt, Effacer les données, Désinstaller), fenêtre de désinstallation, page Play Store, retrait de l'administrateur.
- **Administrateur de l'appareil** : empêche la désinstallation tant qu'il est actif.
- **Désactivation volontaire** : bouton « Demander la désactivation » → le **code à 6 chiffres** ne s'affiche **qu'entre 4h et 5h du matin** (et au moins 10 min après la demande ; passé 5h, il faut attendre le lendemain) → une fois saisi, une fenêtre de 5 min s'ouvre pour retirer l'administrateur et désinstaller. Ensuite la protection se réarme.

Le délai n'est pas raccourcissable depuis l'appli : il repose sur l'horloge, et avancer l'heure du téléphone ne fait que rendre le code disponible plus tôt… mais passe aussi par l'écran de réglages, lui-même bloqué.

En dernier recours (code perdu, bug) : redémarrer en **mode sans échec** coupe les services d'accessibilité tiers, ce qui permet de retirer l'administrateur et de désinstaller.

Navigateurs : Chrome, Edge, Brave (+ Beta et Nightly), Firefox, Vivaldi, Kiwi, Mi Browser, Huawei Browser.

**Protection renforcée pour Brave** (et les navigateurs basés sur Chromium) :
- **Notification « Fermer tous les onglets privés »** : dès qu'un onglet privé existe, même ouvert en arrière-plan, MyControl déclenche cette notification, ce qui ferme tous les onglets privés d'un coup.
- **Raccourci de l'icône** (appui long → « Nouvel onglet privé ») : le navigateur est refermé dès qu'il s'ouvre.
- **Activités privées** (liens ouverts « en privé » depuis une autre appli) : refermées immédiatement.
- Pour que la première protection marche, laisse les **notifications de Brave autorisées**.

**Opera GX / Opera** (désormais bloqués en entier, voir plus bas — la détection fine reste dans le code au cas où on les débloquerait) : le mode privé s'ouvre depuis l'onglet « Privé » du gestionnaire d'onglets. MyControl bloque l'appui sur cet onglet, détecte s'il est actif, et reconnaît l'option « Quitter le mode privé » qui n'apparaît qu'en mode privé. Les boutons pour **quitter** ou **fermer** le mode privé ne sont jamais bloqués.

Depuis la mise à jour « Opera GX renforcé », MyControl repère aussi :
- le passage à l'onglet « Privé » **en balayant** le gestionnaire d'onglets (sans appui), ou par une simple sélection ;
- l'appui sur un bouton dont le libellé « Privé » est dans un sous-élément (icône + texte) ;
- l'appui long, et le raccourci de l'icône d'Opera GX (« Onglet privé ») ;
- la page d'accueil privée d'Opera GX (« Vous êtes en mode privé », « You're in private mode »…).

## Un navigateur passe encore à travers ? Mode diagnostic

Dans l'appli, appuie sur **« Un navigateur passe à travers ? Diagnostic »** → **Démarrer (10 min)**. Ouvre le navigateur, essaie d'ouvrir un onglet privé, puis reviens dans MyControl → **Partager le rapport**. Le rapport liste les textes, identifiants et états (sélectionné, coché) des écrans du navigateur, ainsi que chaque blocage et sa raison. Il ne quitte le téléphone que si tu le partages. Avec ce rapport, il suffit le plus souvent d'ajouter un libellé ou un identifiant dans `detection.json`, sans réinstaller.

**Liste blanche : seuls Chrome et Samsung Internet sont autorisés** (la navigation privée y reste bloquée). Tous les autres navigateurs sont bloqués en entier : dès qu'ils s'ouvrent, MyControl renvoie à l'écran d'accueil, même quand une autre appli ouvre un lien dedans. Sont reconnus :
- une longue liste de navigateurs connus (Edge, Brave, Firefox, Opera/GX, DuckDuckGo, Firefox Focus, Tor, Vivaldi, Kiwi, UC, Yandex, Via…) ;
- **toute appli capable d'ouvrir n'importe quelle adresse web** (c'est la définition d'un navigateur) — y compris un navigateur inconnu ou installé plus tard ;
- toute appli dont le nom de paquet ou le nom affiché trahit un navigateur (« browser », « navigateur », « navegador », « incognito »…), hors explorateurs de fichiers ;
- plus de 150 navigateurs connus, y compris les moins évidents : Ecosia, Qwant, Startpage, Kagi, Arc Search, Comet, Mull, Cromite, Vanadium, Mullvad Browser, navigateurs Xiaomi/Oppo/Vivo/Huawei/Honor, Yandex, Baidu, QQ, Quark, Whale, navigateurs « VPN » et « privés »…

L'appli Google et Google Go restent utilisables, mais leur mode navigation privée est surveillé comme dans Chrome.

La liste blanche (`NAVIGATEURS_AUTORISES` dans `Detection.kt`) est figée dans l'APK : `detection.json` peut seulement **ajouter** des applis à bloquer (`applis_bloquees`), jamais en autoriser. Pense à choisir Chrome ou Samsung Internet comme navigateur par défaut (Paramètres → Applications → Choisir les applis par défaut → Navigateur).

## Obtenir l'APK

### Option A — GitHub (sans rien installer)
1. Crée un dépôt GitHub et pousse ce dossier sur la branche `main`.
2. Onglet **Actions** → le workflow « Compiler l'APK » se lance (≈ 3 min).
3. Ouvre l'exécution terminée → section **Artifacts** → télécharge `MyControl-apk`.

### Option B — Android Studio
Ouvre le dossier, laisse Gradle se synchroniser, puis **Build → Build APK(s)**.

## Installer sur le téléphone
1. Copie `app-debug.apk` sur le téléphone et ouvre-le (autorise l'installation d'applis inconnues).
2. Lance MyControl : l'appli t'affiche un **assistant en 3 étapes** (activer le blocage → empêcher la désinstallation → verrouiller). Suis-le, il n'y a qu'une action à la fois.
3. Android 13+ : si l'option d'accessibilité est grisée, Paramètres → Applications → MyControl → ⋮ → **Autoriser les paramètres restreints**.
4. Samsung / Xiaomi : désactive l'optimisation de batterie pour MyControl, sinon le service peut être coupé.

### À propos des avertissements du téléphone
MyControl s'installe via un APK (hors Play Store), donc Android peut afficher « application non vérifiée » — c'est normal, choisis « Installer quand même ».

En revanche, soyons clairs : une appli qui **combine un service d'accessibilité, l'administrateur d'appareil et le blocage de sa propre désinstallation** correspond au profil que Google Play Protect surveille de près (c'est aussi le profil des logiciels de contrôle abusifs). Ce comportement ne peut pas être « caché » sans retirer ces fonctions. Les libellés de l'appli sont donc volontairement explicites et transparents, et aucune donnée n'est collectée ni envoyée. Si Play Protect affiche une alerte, elle reste contournable par l'utilisateur, mais le risque ne peut pas être ramené à zéro tant que la protection forte est conservée.

## Ajuster la détection

### Sans réinstaller (recommandé) : `detection.json`
Les règles peuvent être mises à jour **à distance**, sans recompiler ni réinstaller l'APK. Il suffit d'éditer `detection.json` à la racine du dépôt :
- `navigateurs` : paquets de navigateurs surveillés ;
- `mots_clic` : mots qui bloquent un clic ;
- `phrases_ecran` / `ids_ecran` : signes qu'un onglet privé est affiché.

Le téléphone récupère ce fichier au démarrage du service (et au plus une fois toutes les 6 h), le met en cache et l'**ajoute** aux listes de base intégrées à l'appli. Si le fichier est injoignable ou invalide, seules les listes de base s'appliquent — la détection n'est jamais interrompue. Comme le service n'est plus limité à une liste figée de paquets, un navigateur ajouté dans `detection.json` est pris en compte sans nouvelle installation.

Pour forcer une mise à jour immédiate : couper puis réactiver le service d'accessibilité de MyControl.

### Dans le code (listes de base)
Les valeurs par défaut, qui servent de filet de sécurité, sont dans `app/src/main/java/fr/axel/mycontrol/Detection.kt` (`NAVIGATEURS_BASE`, `MOTS_CLIC_BASE`, `PHRASES_ECRAN_BASE`, `IDS_ECRAN_BASE`).

## Limites
- La protection freine fortement la désactivation mais n'est pas inviolable (mode sans échec, ADB, réinitialisation). Pour un contrôle parental solide, combiner avec Google Family Link.
- Les navigateurs changent leurs libellés au fil des versions : teste chaque navigateur et ajoute des mots-clés si besoin.
- Les navigateurs « tout privé » (DuckDuckGo, Firefox Focus, Tor Browser…) ne sont pas couverts : il faut les désinstaller ou les bloquer via Family Link.


**Anti-triche horaire** : l'heure du créneau est recalculée à partir du chronomètre interne du téléphone (non réglable) et du fuseau figé au moment de la demande, donc changer l'heure ou le fuseau ne fait pas apparaître le code. Un redémarrage annule la demande en cours.


## Mises à jour (sans désinstaller, sans code)

MyControl se met à jour toute seule : elle vérifie la Release `latest` toutes les 6 h (et à chaque ouverture de l'appli), télécharge le nouvel APK et l'installe par-dessus elle-même. Le service d'accessibilité, l'administrateur et la protection verrouillée sont conservés : **aucun code de déverrouillage n'est nécessaire**. La première mise à jour demande d'autoriser MyControl à installer des applis puis d'appuyer sur « Mettre à jour » ; ensuite (Android 12+), c'est silencieux.

Ça repose sur une **clé de signature fixe**, gardée dans les secrets du dépôt (Settings → Secrets and variables → Actions) :

| Secret | Contenu |
|---|---|
| `MYCONTROL_KEYSTORE_BASE64` | le fichier `mycontrol.jks` encodé en base64 |
| `MYCONTROL_KEYSTORE_PASSWORD` | le mot de passe de la clé |

Garde une copie de la clé en lieu sûr : si elle est perdue, les futures versions ne pourront plus s'installer par-dessus l'ancienne (il faudrait désinstaller).
