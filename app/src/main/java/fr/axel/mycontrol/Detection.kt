package fr.axel.mycontrol

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Règles de détection.
 *
 * Deux sources se cumulent :
 *  - les listes « de base » ci-dessous, intégrées à l'APK (filet de sécurité,
 *    toujours actives même sans réseau) ;
 *  - les listes « distantes », téléchargées par [MiseAJour] depuis
 *    detection.json sur GitHub et mises en cache. Elles s'AJOUTENT à la base,
 *    ce qui permet d'ajouter un navigateur ou un libellé sans réinstaller
 *    l'appli : il suffit d'éditer detection.json sur le dépôt.
 *
 * Si le fichier distant est injoignable ou invalide, seules les listes de
 * base s'appliquent.
 */
object Detection {

    // --- Listes de base (intégrées à l'APK) -------------------------------

    /** Navigateurs surveillés. */
    private val NAVIGATEURS_BASE = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.microsoft.emmx",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "com.sec.android.app.sbrowser",
        "com.opera.browser", "com.opera.browser.beta",
        "com.opera.gx",                        // Opera GX (navigateur gaming)
        "com.opera.mini.native", "com.opera.mini.native.beta",
        "com.opera.touch",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.mi.globalbrowser",
        "com.huawei.browser",
        // Appli Google et Google Go : ont leur propre mode navigation privée,
        // qui est surveillé (l'appli elle-même reste utilisable).
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.searchlite",
    )

    /**
     * LISTE BLANCHE : les seuls navigateurs autorisés. La navigation privée y
     * reste bloquée (détection fine). Tout AUTRE navigateur — connu, inconnu
     * ou installé plus tard — est bloqué en entier.
     * Ne peut PAS être élargie à distance : seul un nouvel APK peut la changer.
     */
    val NAVIGATEURS_AUTORISES = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.sec.android.app.sbrowser",        // Samsung Internet
        "com.sec.android.app.sbrowser.beta",   // Samsung Internet Beta
    )

    /**
     * Applis bloquées EN ENTIER, en plus de la détection automatique : dès
     * qu'elles passent au premier plan, on renvoie à l'écran d'accueil.
     * Navigateurs connus, dont ceux « tout privé » (DuckDuckGo, Focus, Tor…).
     */
    private val APPLIS_BLOQUEES_BASE = setOf(
        "com.sec.android.app.sbrowser.lite",   // Samsung Internet Lite
        "com.opera.browser", "com.opera.browser.beta", "com.opera.gx",
        "com.opera.mini.native", "com.opera.mini.native.beta",
        "com.opera.touch", "com.opera.cryptobrowser",
        "com.microsoft.emmx", "com.microsoft.emmx.beta", "com.microsoft.emmx.dev", "com.microsoft.emmx.canary",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "org.mozilla.focus", "org.mozilla.klar", "org.mozilla.rocket",
        "org.torproject.torbrowser", "org.torproject.torbrowser_alpha",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser", "com.vivaldi.browser.snapshot",
        "com.kiwibrowser.browser",
        "com.mi.globalbrowser", "com.android.browser", "com.huawei.browser",
        "com.UCMobile.intl", "com.uc.browser.en", "com.ucmobile.lite",
        "com.yandex.browser", "com.yandex.browser.lite",
        "com.ecosia.android", "com.aloha.browser", "mark.via.gp", "mark.via",
        "com.cloudmosa.puffin", "com.cloudmosa.puffinFree", "mobi.mgeek.TunnyBrowser",
        "com.ghostery.android.ghostery", "com.mycompany.app.soulbrowser",
        "org.bromite.bromite", "org.cromite.cromite", "us.spotco.fennec_dos",
        "org.ironfoxoss.ironfox", "io.github.forkmaintainers.iceraven",
        "com.stoutner.privacybrowser.standard", "com.stoutner.privacybrowser.free",
        "acr.browser.lightning", "acr.browser.barebones", "com.qwant.liberty",
        "com.startpage.app", "com.hsv.freeadblockerbrowser", "com.apusapps.browser",
        "com.lenovo.browser", "com.oppo.browser", "com.heytap.browser", "com.vivo.browser",
        "com.microsoft.bing", "org.adblockplus.browser", "com.avast.android.secure.browser",
        "com.avg.android.secure.browser", "com.cake.browser", "com.explore.web.browser",
        "com.tcl.browser", "com.transsion.phoenix", "com.opera.app.news",
        // Moteurs de recherche « écolo » / vie privée avec navigateur intégré
        "com.ecosia.android.beta", "com.qwant.mobile", "com.startpage.mobile",
        "com.kagi.search", "com.mojeek.app", "com.brave.search",
        "ai.perplexity.comet", "company.thebrowser.arc",
        // Forks de Firefox / Chromium
        "org.mozilla.fennec_fdroid", "org.mozilla.fennec_aurora", "org.mozilla.firefox.nightly",
        "org.mozilla.focus.beta", "org.mozilla.focus.nightly", "org.mozilla.reference.browser",
        "us.spotco.mulch", "app.vanadium.browser", "org.chromium.chrome", "org.chromium.webview_shell",
        "org.ungoogled.chromium.stable", "org.ungoogled.chromium.extensions.stable",
        "com.thorium.browser", "org.lineageos.jelly", "org.midorinext.android",
        "de.baumann.browser", "net.slions.fulguris.full.playstore", "net.slions.fulguris.full.fdroid",
        "info.guardianproject.orfox", "org.torproject.android", "com.mullvad.browser",
        "io.github.mthli.ninja", "com.chimbori.hermitcrab", "arun.com.chromer",
        "com.jamal2367.styx", "acr.browser.lightning.plus",
        // Navigateurs de constructeurs
        "com.mi.globalbrowser.mini", "com.coloros.browser", "com.realme.browser",
        "com.nearme.browser", "com.hihonor.baidu.browser", "com.talpa.hibrowser",
        "com.lge.browser", "com.asus.browser", "com.zte.browser", "com.motorola.browser",
        "com.htc.sense.browser", "com.sonyericsson.browser",
        // Grand public / « légers » / VPN
        "com.mx.browser", "com.mx.browser.star", "com.cloudmosa.puffinTV", "com.cloudmosa.puffinIncognito",
        "com.yandex.browser.alpha", "com.yandex.browser.beta", "ru.yandex.searchplugin",
        "com.baidu.browser.inter", "com.qihoo.browser", "com.tencent.mtt", "com.tencent.mtt.intl",
        "com.sogou.mobile.explorer", "com.quark.browser", "com.ucturbo",
        "com.norton.securebrowser", "com.kaspersky.safebrowser", "com.bitdefender.browser",
        "com.tunnelbear.browser", "com.cyberghostvpn.browser", "com.epicbrowser.app",
        "com.aloha.browser.lite", "com.opera.mini.android", "com.opera.max.global",
        "com.smartcookie.browser", "com.quetta.browser", "com.yolo.browser",
        "com.fast.browser", "com.lite.browser", "com.incognito.browser",
        "com.private.browser", "privatebrowser.securebrowser.com.webbrowser",
        "com.privatebrowser.securebrowser", "com.hld.anzenbokusu",
        "com.whale.browser", "com.naver.whale", "com.sec.android.app.sbrowser.edge",
    )

    /**
     * Applis (hors navigateurs) interdites EN ENTIER : X (ex-Twitter) et
     * Telegram, avec leurs variantes officielles et clients alternatifs.
     * Figées dans l'APK : detection.json ne peut pas les débloquer.
     */
    val APPLIS_INTERDITES = setOf(
        // X (ex-Twitter)
        "com.twitter.android", "com.twitter.android.lite", "com.twitter.android.beta",
        "com.x.android",
        // Telegram (officiel, Play Store, site web, bêta) et Telegram X
        "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta",
        "org.telegram.messenger.lite", "org.thunderdog.challegram",
        // Clients Telegram alternatifs
        "org.telegram.plus", "tw.nekomimi.nekogram", "xyz.nextalone.nagram",
        "nekox.messenger", "com.exteragram.messenger", "uz.unnarsx.cherrygram",
        "org.forkgram.messenger", "it.octogram.android", "com.iMe.android",
        "org.telegram.bgram", "ir.ilmili.telegraph", "app.nicegram",
    )

    /** X ou Telegram (pour afficher le bon message). */
    fun estAppliInterdite(paquet: String) = paquet in APPLIS_INTERDITES

    /**
     * Applis qui savent ouvrir n'importe quel lien web sans être des
     * navigateurs : jamais bloquées par la détection automatique.
     */
    private val JAMAIS_BLOQUEES = setOf(
        "com.google.android.googlequicksearchbox", // Appli Google (mode privé surveillé)
        "com.google.android.apps.searchlite",      // Google Go (mode privé surveillé)
        "com.google.android.gm",                   // Gmail
        "com.google.android.apps.docs",            // Drive
        "com.google.android.youtube",
        "com.android.vending",                     // Play Store
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.google.android.webview", "com.android.webview",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.galaxyfinder",
    )

    /** Mots dans le nom de paquet qui trahissent un navigateur. */
    private val MOTS_PAQUET_NAVIGATEUR = listOf(
        "browser", "navigateur", "navegador", "browse", "incognito",
        "privateweb", "secureweb", "webexplorer", "explorer.web",
    )

    /** Mots dans le NOM AFFICHÉ de l'appli (toutes langues courantes). */
    private val MOTS_NOM_NAVIGATEUR = listOf(
        "web browser", "browser web", "internet browser", "private browser",
        "navigateur web", "navigateur internet", "navegador", "navigatore",
        "incognito", "navigation privée", "webbrowser",
    )

    /** Faux positifs : explorateurs de FICHIERS, pas de sites web. */
    private val MOTS_FICHIERS = listOf("file", "fichier", "document", "photo", "gallery", "music")

    /** Applis système d'où l'on peut désactiver ou désinstaller MyControl. */
    private val PAQUETS_REGLAGES_BASE = setOf(
        "com.android.settings",                // Réglages (Pixel, Samsung, la plupart)
        "com.samsung.accessibility",           // Accessibilité Samsung
        "com.miui.securitycenter",             // Infos appli Xiaomi
        "com.google.android.packageinstaller", // Fenêtre « Désinstaller ? »
        "com.android.packageinstaller",
        "com.android.vending",                 // Bouton Désinstaller du Play Store
    )

    /** Un clic sur un élément contenant ces mots ouvre (presque toujours) le mode privé. */
    private val MOTS_CLIC_BASE = listOf(
        "privé",          // « Nouvel onglet de navigation privée », « Onglets privés »
        "private",        // « New private tab », « Private browsing »
        "incognito",
        "inprivate",
        "mode secret",
        "secret mode",
    )

    /** Phrases visibles seulement quand on est DANS un onglet privé. */
    private val PHRASES_ECRAN_BASE = listOf(
        "vous êtes en navigation privée",          // Chrome / Brave FR
        "gone incognito",                          // Chrome EN « You've gone Incognito »
        "navigation inprivate",                    // Edge FR
        "inprivate browsing",                      // Edge EN
        "browsing inprivate",
        "vous naviguez en mode privé",
        "fenêtre de navigation privée",            // Brave FR
        "private window with tor",                 // Brave + Tor
        "fenêtre privée avec tor",
        "firefox efface votre historique",         // Firefox FR (accueil privé)
        "firefox clears your search and browsing", // Firefox EN
        "mode secret activé",                      // Samsung Internet
        "mode secret est activé",                  // Samsung Internet (variante FR)
        "secret mode is on",
        "turn on secret mode",                     // écran d'intro Samsung
        // Opera / Opera GX : options visibles seulement EN mode privé
        "quitter le mode privé",
        "leave private mode",
        "exit private mode",
        "browsing in private mode",                // « You are browsing in private mode »
        "navigation en mode privé",
        // Opera GX : page d'accueil des onglets privés (FR / EN)
        "vous êtes en mode privé",
        "vous naviguez en privé",
        "you're in private mode",
        "you are in private mode",
        "you're browsing privately",
        "private mode is on",
        "mode privé activé",
        "won't save your browsing history",
        "n'enregistrera pas votre historique",
        "ne conservera pas votre historique",
    )

    /** Fragments d'identifiants de vues propres au mode privé. */
    private val IDS_ECRAN_BASE = listOf(
        "incognito_badge",
        "incognito_ntp",
        "incognito_description",
        "new_tab_incognito",
        "inprivate",
        "private_browsing_description",
        // Samsung Internet (mode Secret)
        "secret_mode",
        "secret_mode_description",
        "secret_mode_intro",
        "secret_mode_guide",
        // Brave (page d'accueil des onglets privés)
        "private_ntp",
        "private_tab_ntp",
        // Opera / Opera GX
        "private_mode_ntp",
        "private_mode_empty",
        "private_tabs_empty",
        "private_mode_page",
        "private_start_page",
        "private_tab_page",
        "private_tabs_page",
        "private_tabs_list",
        "private_tab_list",
        "incognito_page",
    )

    /**
     * Libellés de LANCEMENT direct d'un onglet privé, reconnus dans N'IMPORTE
     * quelle appli (surtout le lanceur : appui long sur l'icône de Brave →
     * « Nouvel onglet privé »). Volontairement très précis pour éviter les
     * faux positifs hors navigateur.
     */
    private val LANCEMENTS_PRIVES_BASE = listOf(
        "nouvel onglet privé", "new private tab",
        "nouvel onglet de navigation privée", "nouvel onglet navigation privée",
        "new incognito tab", "nouvel onglet incognito",
        "new inprivate tab", "nouvel onglet inprivate",
        // Opera GX (appui long sur l'icône)
        "onglet privé", "private tab",
        "nouvelle fenêtre privée", "new private window",
    )

    /**
     * Notification « Fermer tous les onglets privés » que Brave (et Chrome,
     * Edge, Vivaldi…) affichent tant qu'un onglet privé est ouvert. La
     * déclencher ferme TOUS les onglets privés d'un coup.
     */
    private val MOTS_NOTIF_FERMER_BASE = listOf(
        "fermer tous", "fermer les onglets", "close all",
    )

    /** Fenêtres d'installation d'Android (utilisées par les mises à jour de MyControl). */
    val PAQUETS_INSTALLATEUR = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    /** Applis système : NON mises à jour à distance (écrans anti-désactivation sensibles). */
    val PAQUETS_REGLAGES: Set<String> get() = PAQUETS_REGLAGES_BASE

    /** Mots qui, avec « MyControl » à l'écran, signalent un écran sensible. */
    private val MOTS_REGLAGES = listOf(
        "désinstaller", "uninstall",
        "forcer l'arrêt", "force stop", "arrêt forcé",
        "désactiver", "deactivate", "turn off",
        "utiliser mycontrol", "use mycontrol",
        "effacer les données", "clear data", "vider le stockage", "clear storage",
    )

    /** Mots qui signalent un bouton/bascule présent aussi en mode normal → ignorés pour l'écran. */
    private val IDS_EXCLUS = listOf("toggle", "switch", "button", "menu", "tab_switcher")

    // --- Listes effectives (base + distant), précalculées une seule fois ---
    // Recalculées uniquement dans chargerDistant(), pas à chaque événement :
    // onAccessibilityEvent est un chemin très chaud (toutes les applis).

    @Volatile private var navigateurs: Set<String> = NAVIGATEURS_BASE
    @Volatile private var motsClic: List<String> = MOTS_CLIC_BASE
    @Volatile private var phrasesEcran: List<String> = PHRASES_ECRAN_BASE
    @Volatile private var idsEcran: List<String> = IDS_ECRAN_BASE
    @Volatile private var applisBloquees: Set<String> = APPLIS_BLOQUEES_BASE + APPLIS_INTERDITES
    @Volatile private var lancementsPrives: List<String> = LANCEMENTS_PRIVES_BASE

    val NAVIGATEURS: Set<String> get() = navigateurs

    /**
     * Applis bloquées en entier. La base ne peut pas être retirée à distance :
     * detection.json peut seulement AJOUTER des applis (clé « applis_bloquees »).
     */
    val APPLIS_BLOQUEES: Set<String> get() = applisBloquees

    /**
     * Applique la config distante mise en cache par [MiseAJour].
     * Tolérant : une clé absente ou malformée est simplement ignorée, les
     * listes de base restant actives.
     */
    fun chargerDistant(contexte: Context) {
        val json = MiseAJour.jsonEnCache(contexte) ?: return
        try {
            val o = JSONObject(json)
            navigateurs = NAVIGATEURS_BASE + lireListe(o, "navigateurs")
            motsClic = MOTS_CLIC_BASE + lireListe(o, "mots_clic").map { it.lowercase(Locale.ROOT) }
            phrasesEcran = PHRASES_ECRAN_BASE + lireListe(o, "phrases_ecran").map { it.lowercase(Locale.ROOT) }
            idsEcran = IDS_ECRAN_BASE + lireListe(o, "ids_ecran").map { it.lowercase(Locale.ROOT) }
            // Jamais d'appli système de réglages ni MyControl lui-même : ce
            // serait un moyen de se bloquer hors du téléphone.
            lancementsPrives = LANCEMENTS_PRIVES_BASE + lireListe(o, "lancements_prives").map { it.lowercase(Locale.ROOT) }
            applisBloquees = APPLIS_BLOQUEES_BASE + APPLIS_INTERDITES + lireListe(o, "applis_bloquees")
                .filter { it !in PAQUETS_REGLAGES_BASE && it != contexte.packageName && it !in NAVIGATEURS_AUTORISES }
            oublierNavigateurs()
        } catch (_: Exception) {
            // JSON invalide : on garde la base + ce qui était déjà chargé.
        }
    }

    // --- Liste blanche : tout navigateur non autorisé est bloqué -----------

    /** Résultat de la détection par appli (le chemin d'événements est très chaud). */
    private val cacheNavigateurs = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** À appeler quand une appli est installée / mise à jour / supprimée. */
    fun oublierNavigateurs() = cacheNavigateurs.clear()

    /**
     * Cette appli doit-elle être bloquée EN ENTIER ? Oui si c'est un
     * navigateur qui n'est pas dans [NAVIGATEURS_AUTORISES] :
     *  - liste connue (base + detection.json) ;
     *  - OU l'appli déclare savoir ouvrir n'importe quelle adresse web
     *    (http/https vers un site quelconque) : c'est la définition même d'un
     *    navigateur, ce qui attrape aussi les navigateurs inconnus ou installés
     *    après coup ;
     *  - OU son nom de paquet contient « browser ».
     */
    fun estBloqueeEnEntier(contexte: Context, paquet: String): Boolean {
        if (paquet in NAVIGATEURS_AUTORISES) return false
        if (paquet in applisBloquees) return true
        if (paquet == contexte.packageName || paquet in PAQUETS_REGLAGES_BASE ||
            paquet in PAQUETS_INSTALLATEUR || paquet in JAMAIS_BLOQUEES
        ) return false
        return cacheNavigateurs.getOrPut(paquet) { estNavigateur(contexte, paquet) }
    }

    private fun estNavigateur(contexte: Context, paquet: String): Boolean {
        if (paquet in navigateurs) return true
        val p = paquet.lowercase(Locale.ROOT)
        val fichiers = MOTS_FICHIERS.any { p.contains(it) }
        if (!fichiers && MOTS_PAQUET_NAVIGATEUR.any { p.contains(it) }) return true
        return try {
            val pm = contexte.packageManager
            val nom = try {
                pm.getApplicationLabel(pm.getApplicationInfo(paquet, 0)).toString().lowercase(Locale.ROOT)
            } catch (_: Exception) { "" }
            if (MOTS_NOM_NAVIGATEUR.any { nom.contains(it) }) return true
            listOf("http", "https").any { schema ->
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("$schema://exemple-mycontrol.com/")
                ).addCategory(android.content.Intent.CATEGORY_BROWSABLE).setPackage(paquet)
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0).isNotEmpty()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun lireListe(o: JSONObject, cle: String): List<String> {
        val arr = o.optJSONArray(cle) ?: return emptyList()
        val sortie = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optString(i, null)?.takeIf { it.isNotBlank() }?.let { sortie.add(it) }
        }
        return sortie
    }

    // --- Détection --------------------------------------------------------

    private fun normaliser(texte: CharSequence?): String? =
        texte?.toString()?.lowercase(Locale.ROOT)?.replace('’', '\'')

    fun estEcranReglagesSensible(texteEcran: String): Boolean {
        val t = normaliser(texteEcran) ?: return false
        return t.contains("mycontrol") && MOTS_REGLAGES.any { t.contains(it) }
    }

    /** Le texte parle-t-il de désinstallation (et pas seulement de mise à jour) ? */
    fun parleDeDesinstallation(texteEcran: String): Boolean {
        val t = normaliser(texteEcran) ?: return false
        return t.contains("désinstaller") || t.contains("uninstall")
    }

    /** Mots de SORTIE du mode privé : ces clics ne doivent jamais être bloqués. */
    private val MOTS_SORTIE = listOf("quitter", "leave", "exit", "fermer", "close")

    fun estClicPrive(texte: CharSequence?): Boolean {
        val t = normaliser(texte) ?: return false
        if (MOTS_SORTIE.any { t.contains(it) }) return false
        return motsClic.any { t.contains(it) }
    }

    /**
     * Onglet « Privé » SÉLECTIONNÉ dans le gestionnaire d'onglets (Opera GX,
     * Opera, Brave, Chrome…) : un libellé court, coché/sélectionné, qui n'est
     * pas un interrupteur de réglage.
     */
    fun estOngletPriveSelectionne(texte: CharSequence?, nomClasse: CharSequence?): Boolean {
        val t = normaliser(texte) ?: return false
        if (t.length > 25) return false
        val c = normaliser(nomClasse) ?: ""
        if (c.contains("switch") || c.contains("checkbox")) return false
        if (MOTS_SORTIE.any { t.contains(it) }) return false
        return motsClic.any { t.contains(it) }
    }

    fun estPhraseEcranPrive(texte: CharSequence?): Boolean {
        val t = normaliser(texte) ?: return false
        return phrasesEcran.any { t.contains(it) }
    }

    fun estIdEcranPrive(id: String?): Boolean {
        val t = normaliser(id) ?: return false
        if (IDS_EXCLUS.any { t.contains(it) }) return false
        return idsEcran.any { t.contains(it) }
    }

    /** Clic (hors navigateur) qui lance directement un onglet privé ? */
    fun estLancementPrive(texte: CharSequence?): Boolean {
        val t = normaliser(texte) ?: return false
        return lancementsPrives.any { t.contains(it) }
    }

    /** Texte de la notification « Fermer tous les onglets privés » ? */
    fun estNotifOngletsPrives(texte: CharSequence?): Boolean {
        val t = normaliser(texte) ?: return false
        return MOTS_NOTIF_FERMER_BASE.any { t.contains(it) } && motsClic.any { t.contains(it) }
    }

    /** Activité dédiée au mode privé (ex. IncognitoCustomTabActivity de Chromium/Brave). */
    fun estActivitePrivee(nomClasse: CharSequence?): Boolean {
        val t = normaliser(nomClasse) ?: return false
        return t.contains("incognito") || t.contains("privatetab") || t.contains("privatebrowsing")
    }
}
