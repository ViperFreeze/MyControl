package fr.axel.mycontrol

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Met à jour les règles de détection à distance, sans réinstaller l'appli.
 *
 * Le fichier detection.json est hébergé sur le dépôt GitHub. À chaque
 * démarrage du service (et au plus une fois par [INTERVALLE_MS]), l'appli
 * le télécharge en arrière-plan et le met en cache. [Detection] ajoute
 * ensuite ces règles à ses listes de base.
 *
 * Tout est « best effort » : pas de réseau, serveur injoignable ou JSON
 * invalide → on garde le dernier cache valide, ou à défaut les listes de
 * base intégrées à l'APK. La détection n'est jamais interrompue.
 */
object MiseAJour {

    /** URL brute du fichier de config sur GitHub (branche main). */
    private const val URL_CONFIG =
        "https://raw.githubusercontent.com/ViperFreeze/MyControl/main/detection.json"

    private const val FICHIER = "maj"
    private const val CLE_JSON = "json"
    private const val CLE_DERNIER = "dernier_ms"

    /** Intervalle minimal entre deux téléchargements. */
    private const val INTERVALLE_MS = 6 * 60 * 60 * 1000L // 6 h

    private fun prefs(c: Context) =
        c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** Dernière config valide mise en cache (null si aucune). */
    fun jsonEnCache(c: Context): String? = prefs(c).getString(CLE_JSON, null)

    /**
     * Charge le cache existant immédiatement, puis télécharge une version
     * fraîche en arrière-plan si l'intervalle est écoulé. À appeler au
     * démarrage du service.
     */
    fun planifier(c: Context) {
        Detection.chargerDistant(c) // applique tout de suite le dernier cache

        val dernier = prefs(c).getLong(CLE_DERNIER, 0)
        if (System.currentTimeMillis() - dernier < INTERVALLE_MS) return

        Thread {
            try { telecharger(c) } catch (_: Exception) { /* silencieux */ }
        }.apply { isDaemon = true }.start()
    }

    private fun telecharger(c: Context) {
        val conn = (URL(URL_CONFIG).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return
            val texte = conn.inputStream.bufferedReader().use { it.readText() }

            // On ne met en cache que si le JSON est analysable.
            JSONObject(texte)

            prefs(c).edit()
                .putString(CLE_JSON, texte)
                .putLong(CLE_DERNIER, System.currentTimeMillis())
                .apply()

            Detection.chargerDistant(c) // applique la nouvelle config
        } finally {
            conn.disconnect()
        }
    }
}
