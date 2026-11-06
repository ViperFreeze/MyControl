package fr.axel.mycontrol

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.security.SecureRandom
import java.util.Calendar
import java.util.TimeZone

/**
 * Protection par délai.
 *
 * Principe : une fois la protection « verrouillée », les écrans qui
 * permettent d'arrêter ou de désinstaller MyControl sont bloqués.
 * Pour les rouvrir, il faut :
 *   1. lancer une « demande de désactivation » ;
 *   2. attendre au moins 10 minutes ET que l'heure soit entre 4h et 5h
 *      du matin : le code n'est visible que dans ce créneau ;
 *   3. entrer le code qui s'affiche alors.
 *
 * Anti-triche : l'heure n'est pas lue sur l'horloge du téléphone (qu'on
 * peut avancer) mais recalculée depuis le moment de la demande avec le
 * chronomètre interne (elapsedRealtime), qui ne se règle pas. Le fuseau
 * horaire est figé au moment de la demande. Un redémarrage remettant ce
 * chronomètre à zéro, il annule la demande en cours.
 * Le code valide ouvre une fenêtre de quelques minutes pendant laquelle
 * l'administrateur peut être retiré et l'appli désinstallée. Ensuite la
 * protection se réarme automatiquement.
 */
object Securite {
    private const val FICHIER = "securite"

    /** Attente minimale après la demande, même si on est déjà dans le créneau. */
    const val DELAI_MS = 10 * 60_000L

    /** Créneau (heure locale) pendant lequel le code est disponible : [4h, 5h[. */
    const val HEURE_DEBUT = 4
    const val HEURE_FIN = 5

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

    private fun nbDemarrages(c: Context) =
        Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun demandeEnCours(c: Context): Boolean {
        val p = prefs(c)
        if (!p.contains("demande_a")) return false
        // Redémarrage depuis la demande -> chronomètre remis à zéro -> demande annulée.
        val redemarre = nbDemarrages(c) != p.getInt("demande_boot", -1) ||
            SystemClock.elapsedRealtime() < p.getLong("demande_chrono", 0)
        if (redemarre) { annulerDemande(c); return false }
        return true
    }

    /** Démarre l'attente et fige un code tiré au hasard. */
    fun demanderDesactivation(c: Context) {
        val code = (SecureRandom().nextInt(900_000) + 100_000).toString() // 6 chiffres
        prefs(c).edit()
            .putLong("demande_a", maintenant())
            .putLong("demande_chrono", SystemClock.elapsedRealtime())
            .putInt("demande_boot", nbDemarrages(c))
            .putString("demande_fuseau", TimeZone.getDefault().id)
            .putString("code", code)
            .apply()
    }

    fun annulerDemande(c: Context) {
        prefs(c).edit()
            .remove("demande_a").remove("demande_chrono").remove("demande_boot")
            .remove("demande_fuseau").remove("code")
            .apply()
    }

    /** Heure « fiable » : heure de la demande + temps réellement écoulé depuis. */
    private fun maintenantFiable(c: Context): Long {
        val p = prefs(c)
        return p.getLong("demande_a", 0) +
            (SystemClock.elapsedRealtime() - p.getLong("demande_chrono", 0))
    }

    private fun fuseau(c: Context): TimeZone =
        TimeZone.getTimeZone(prefs(c).getString("demande_fuseau", null) ?: TimeZone.getDefault().id)

    private fun dansCreneau(t: Long, tz: TimeZone): Boolean {
        val h = Calendar.getInstance(tz).apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
        return h in HEURE_DEBUT until HEURE_FIN
    }

    /** Premier instant >= t situé dans le créneau 4h-5h. */
    private fun prochainCreneau(t: Long, tz: TimeZone): Long {
        if (dansCreneau(t, tz)) return t
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, HEURE_DEBUT)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= t) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    /** Temps restant avant que le code devienne visible (0 = disponible maintenant). */
    fun attenteRestanteMs(c: Context): Long {
        if (!demandeEnCours(c)) return 0
        val tz = fuseau(c)
        val now = maintenantFiable(c)
        val minimum = prefs(c).getLong("demande_a", 0) + DELAI_MS
        return (prochainCreneau(maxOf(now, minimum), tz) - now).coerceAtLeast(0)
    }

    /** Heure de disponibilité du code, en heure locale réelle (pour l'affichage). */
    fun disponibleA(c: Context): Long = maintenant() + attenteRestanteMs(c)

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
