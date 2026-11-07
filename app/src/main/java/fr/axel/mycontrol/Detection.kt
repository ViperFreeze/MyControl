package fr.axel.mycontrol

import java.util.Locale

/**
 * Règles de détection. Si un navigateur passe entre les mailles
 * (nouvelle version, autre langue), c'est ici qu'il faut ajouter des mots-clés.
 */
object Detection {

    /** Navigateurs surveillés (garder synchronisé avec accessibility_config.xml). */
    val NAVIGATEURS = setOf(
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

    /** Applis système d'où l'on peut désactiver ou désinstaller MyControl. */
    val PAQUETS_REGLAGES = setOf(
        "com.android.settings",                // Réglages (Pixel, Samsung, la plupart)
        "com.samsung.accessibility",           // Accessibilité Samsung
        "com.miui.securitycenter",             // Infos appli Xiaomi
        "com.google.android.packageinstaller", // Fenêtre « Désinstaller ? »
        "com.android.packageinstaller",
        "com.android.vending",                 // Bouton Désinstaller du Play Store
    )

    /** Mots qui, avec « MyControl » à l'écran, signalent un écran sensible. */
    private val MOTS_REGLAGES = listOf(
        "désinstaller", "uninstall",
        "forcer l'arrêt", "force stop", "arrêt forcé",
        "désactiver", "deactivate", "turn off",
        "utiliser mycontrol", "use mycontrol",
        "effacer les données", "clear data", "vider le stockage", "clear storage",
    )

    fun estEcranReglagesSensible(texteEcran: String): Boolean {
        val t = normaliser(texteEcran) ?: return false
        return t.contains("mycontrol") && MOTS_REGLAGES.any { t.contains(it) }
    }

    /** Un clic sur un élément contenant ces mots ouvre (presque toujours) le mode privé. */
    private val MOTS_CLIC = listOf(
        "privé",          // « Nouvel onglet de navigation privée », « Onglets privés »
        "private",        // « New private tab », « Private browsing »
        "incognito",
        "inprivate",
        "mode secret",
        "secret mode",
    )

    /** Phrases visibles seulement quand on est DANS un onglet privé. */
    private val PHRASES_ECRAN = listOf(
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
    private val IDS_ECRAN = listOf(
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

    /** Mots qui signalent un bouton/bascule présent aussi en mode normal → ignorés pour l'écran. */
    private val IDS_EXCLUS = listOf("toggle", "switch", "button", "menu", "tab_switcher")

    private fun normaliser(texte: CharSequence?): String? =
        texte?.toString()?.lowercase(Locale.ROOT)?.replace('’', '\'')

    fun estClicPrive(texte: CharSequence?): Boolean {
        val t = normaliser(texte) ?: return false
        return MOTS_CLIC.any { t.contains(it) }
    }

    fun estPhraseEcranPrive(texte: CharSequence?): Boolean {
        val t = normaliser(texte) ?: return false
        return PHRASES_ECRAN.any { t.contains(it) }
    }

    fun estIdEcranPrive(id: String?): Boolean {
        val t = normaliser(id) ?: return false
        if (IDS_EXCLUS.any { t.contains(it) }) return false
        return IDS_ECRAN.any { t.contains(it) }
    }
}
