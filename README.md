# MyControl

Appli Android qui bloque la navigation privée (Incognito, InPrivate, Mode secret…) dans les principaux navigateurs.

## Fonctionnement

Un **service d'accessibilité** surveille uniquement les navigateurs listés :

1. **Clic intercepté** : un appui sur « Nouvel onglet de navigation privée », « Incognito », « InPrivate », « Mode secret »… déclenche immédiatement un *Retour*.
2. **Écran détecté** : si un onglet privé s'affiche quand même, l'appli renvoie à l'écran d'accueil.

Aucune donnée n'est stockée (à part un compteur de blocages) ni envoyée.

## Protection anti-désactivation

- **Code PIN** (4 à 8 chiffres, haché + salé, 1 min d'attente après 5 erreurs).
- Tant que le PIN est actif, le service **bloque les écrans des réglages** qui permettent d'arrêter MyControl : page du service d'accessibilité, infos de l'appli (Forcer l'arrêt, Effacer les données, Désinstaller), fenêtre de désinstallation, page Play Store, écran administrateur.
- **Administrateur de l'appareil** : empêche la désinstallation tant qu'il est actif ; sa désactivation est elle-même bloquée sans PIN.
- **Déverrouiller 5 minutes** (avec le PIN) pour désactiver ou désinstaller volontairement.

En cas d'oubli du PIN : redémarrer en **mode sans échec** (les services d'accessibilité tiers y sont coupés), puis désactiver l'administrateur et désinstaller.

Navigateurs : Chrome, Edge, Brave, Firefox, Samsung Internet, Opera, Vivaldi, Kiwi, Mi Browser, Huawei Browser.

## Obtenir l'APK

### Option A — GitHub (sans rien installer)
1. Crée un dépôt GitHub et pousse ce dossier sur la branche `main`.
2. Onglet **Actions** → le workflow « Compiler l'APK » se lance (≈ 3 min).
3. Ouvre l'exécution terminée → section **Artifacts** → télécharge `MyControl-apk`.

### Option B — Android Studio
Ouvre le dossier, laisse Gradle se synchroniser, puis **Build → Build APK(s)**.

## Installer sur le téléphone
1. Copie `app-debug.apk` sur le téléphone et ouvre-le (autorise l'installation d'applis inconnues).
2. Lance MyControl → **Activer dans Accessibilité** → MyControl → activer.
3. Android 13+ : si l'option est grisée, Paramètres → Applications → MyControl → ⋮ → **Autoriser les paramètres restreints**.
4. Samsung / Xiaomi : désactive l'optimisation de batterie pour MyControl, sinon le service peut être coupé.

## Ajuster la détection
Tout est dans `app/src/main/java/fr/axel/mycontrol/Detection.kt` :
- `MOTS_CLIC` : mots qui bloquent un clic ;
- `PHRASES_ECRAN` / `IDS_ECRAN` : signes qu'un onglet privé est affiché ;
- `NAVIGATEURS` : paquets surveillés (à synchroniser avec `res/xml/accessibility_config.xml`).

## Limites
- La protection freine fortement la désactivation mais n'est pas inviolable (mode sans échec, ADB, réinitialisation). Pour un contrôle parental solide, combiner avec Google Family Link.
- Les navigateurs changent leurs libellés au fil des versions : teste chaque navigateur et ajoute des mots-clés si besoin.
- Les navigateurs « tout privé » (DuckDuckGo, Firefox Focus, Tor Browser…) ne sont pas couverts : il faut les désinstaller ou les bloquer via Family Link.
