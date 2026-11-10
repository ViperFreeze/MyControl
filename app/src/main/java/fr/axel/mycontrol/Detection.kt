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
        "com.brave.browser",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "com.sec.android.app.sbrowser",
        "com.opera.browser", "com.opera.gx", "com.opera.mini.native",
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

    fun estClicPrive(texte: CharSequence?): Boolean {
        val t = normaliser(texte) ?: return false
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
}
