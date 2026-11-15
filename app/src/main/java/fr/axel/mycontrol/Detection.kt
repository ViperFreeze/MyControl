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
    )

    /**
     * Applis bloquées EN ENTIER : dès qu'elles passent au premier plan, on
     * renvoie à l'écran d'accueil. Samsung Internet est bloqué complètement
     * car son mode Secret est difficile à détecter de façon fiable.
     */
    private val APPLIS_BLOQUEES_BASE = setOf(
        "com.sec.android.app.sbrowser",        // Samsung Internet
        "com.sec.android.app.sbrowser.beta",   // Samsung Internet Beta
        "com.sec.android.app.sbrowser.lite",   // Samsung Internet Lite
    )

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
    @Volatile private var applisBloquees: Set<String> = APPLIS_BLOQUEES_BASE
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
            applisBloquees = APPLIS_BLOQUEES_BASE + lireListe(o, "applis_bloquees")
                .filter { it !in PAQUETS_REGLAGES_BASE && it != contexte.packageName }
        } catch (_: Exception) {
            // JSON invalide : on garde la base + ce qui était déjà chargé.
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
