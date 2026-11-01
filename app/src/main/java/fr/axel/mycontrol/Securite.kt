package fr.axel.mycontrol

import android.content.Context
import java.security.SecureRandom

/**
 * Protection par délai.
 *
 * Principe : une fois la protection « verrouillée », les écrans qui
 * permettent d'arrêter ou de désinstaller MyControl sont bloqués.
 * Pour les rouvrir, il faut :
 *   1. lancer une « demande de désactivation » ;
 *   2. attendre 10 minutes (impossible à raccourcir) ;
 *   3. entrer le code qui s'affiche alors.
 * Le code valide ouvre une fenêtre de quelques minutes pendant laquelle
 * l'administrateur peut être retiré et l'appli désinstallée. Ensuite la
 * protection se réarme automatiquement.
 */
object Securite {
    private const val FICHIER = "securite"

    /** Attente incompressible avant que le code soit disponible. */
    const val DELAI_MS = 10 * 60_000L

    /** Durée de la fenêtre de déverrouillage une fois le code validé. */
    const val FENETRE_MS = 5 * 60_000L

    private fun prefs(c: Context) = c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
    private fun maintenant() = System.currentTimeMillis()

    // --- État de la protection --------------------------------------------

    /** La protection est-elle armée ? (seul le code peut l'ouvrir) */
    fun protege(c: Context) = prefs(c).getBoolean("protege", false)

    fun armer(c: Context) {
        prefs(c).edit()
            .putBoolean("protege", true)
            .remove("demande_a")
            .remove("code")
            .putLong("fenetre_jusqua", 0)
            .apply()
    }

    // --- Demande de désactivation -----------------------------------------

    fun demandeEnCours(c: Context) = prefs(c).contains("demande_a")

    /** Démarre l'attente de 10 min et fige un code tiré au hasard. */
    fun demanderDesactivation(c: Context) {
        val code = (SecureRandom().nextInt(900_000) + 100_000).toString() // 6 chiffres
        prefs(c).edit()
            .putLong("demande_a", maintenant())
            .putString("code", code)
            .apply()
    }

    fun annulerDemande(c: Context) {
        prefs(c).edit().remove("demande_a").remove("code").apply()
    }

    /** Temps restant avant que le code devienne visible (0 = disponible). */
    fun attenteRestanteMs(c: Context): Long {
        if (!demandeEnCours(c)) return 0
        val fin = prefs(c).getLong("demande_a", 0) + DELAI_MS
        return (fin - maintenant()).coerceAtLeast(0)
    }

    fun codeDisponible(c: Context) = demandeEnCours(c) && attenteRestanteMs(c) == 0L

    /** Le code n'est renvoyé qu'une fois l'attente écoulée. */
    fun code(c: Context): String? = if (codeDisponible(c)) prefs(c).getString("code", null) else null

    /** Vérifie le code ; s'il est bon, ouvre la fenêtre de déverrouillage. */
    fun validerCode(c: Context, saisie: String): Boolean {
        if (!codeDisponible(c)) return false
        if (saisie.trim() != prefs(c).getString("code", null)) return false
        prefs(c).edit()
            .remove("demande_a")
            .remove("code")
            .putLong("fenetre_jusqua", maintenant() + FENETRE_MS)
            .apply()
        return true
    }

    // --- Fenêtre de déverrouillage ----------------------------------------

    fun deverrouilleRestantMs(c: Context) =
        (prefs(c).getLong("fenetre_jusqua", 0) - maintenant()).coerceAtLeast(0)

    fun reverrouiller(c: Context) {
        prefs(c).edit().putLong("fenetre_jusqua", 0).apply()
    }

    /** Les réglages sensibles sont bloqués si la protection est armée hors fenêtre. */
    fun reglagesProteges(c: Context) = protege(c) && deverrouilleRestantMs(c) == 0L
}
