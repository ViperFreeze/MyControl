package fr.axel.mycontrol

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Code PIN (haché + salé) et fenêtre de déverrouillage temporaire.
 * Tant que le PIN est défini et que l'appli n'est pas déverrouillée,
 * le service bloque les réglages permettant de désactiver MyControl.
 */
object Securite {
    private const val FICHIER = "securite"
    private const val MAX_ECHECS = 5
    private const val ATTENTE_MS = 60_000L
    const val DUREE_DEVERROUILLAGE_MS = 5 * 60_000L

    private fun prefs(c: Context) = c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
    private fun maintenant() = System.currentTimeMillis()

    fun pinDefini(c: Context) = prefs(c).contains("pin_hash")

    fun pinValide(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }

    fun definirPin(c: Context, pin: String) {
        val sel = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        prefs(c).edit()
            .putString("sel", sel)
            .putString("pin_hash", hacher(sel, pin))
            .putInt("echecs", 0)
            .apply()
    }

    /** Vérifie le PIN. Après 5 erreurs, bloque les essais pendant 1 minute. */
    fun verifier(c: Context, pin: String): Boolean {
        if (attenteRestanteMs(c) > 0) return false
        val p = prefs(c)
        val ok = hacher(p.getString("sel", "") ?: "", pin) == p.getString("pin_hash", null)
        if (ok) {
            p.edit().putInt("echecs", 0).apply()
        } else {
            val echecs = p.getInt("echecs", 0) + 1
            val edition = p.edit()
            if (echecs >= MAX_ECHECS) {
                edition.putInt("echecs", 0).putLong("attente_jusqua", maintenant() + ATTENTE_MS)
            } else {
                edition.putInt("echecs", echecs)
            }
            edition.apply()
        }
        return ok
    }

    fun attenteRestanteMs(c: Context) =
        (prefs(c).getLong("attente_jusqua", 0) - maintenant()).coerceAtLeast(0)

    fun deverrouiller(c: Context, dureeMs: Long = DUREE_DEVERROUILLAGE_MS) {
        prefs(c).edit().putLong("deverrouille_jusqua", maintenant() + dureeMs).apply()
    }

    fun verrouiller(c: Context) {
        prefs(c).edit().putLong("deverrouille_jusqua", 0).apply()
    }

    fun deverrouilleRestantMs(c: Context) =
        (prefs(c).getLong("deverrouille_jusqua", 0) - maintenant()).coerceAtLeast(0)

    /** Les réglages sont protégés si un PIN existe et qu'on n'est pas en déverrouillage. */
    fun reglagesProteges(c: Context) = pinDefini(c) && deverrouilleRestantMs(c) == 0L

    private fun hacher(sel: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((sel + pin).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
