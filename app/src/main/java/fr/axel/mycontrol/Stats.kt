package fr.axel.mycontrol

import android.content.Context

/** Compteur des tentatives bloquées. */
object Stats {
    private const val FICHIER = "stats"
    private const val CLE = "bloquages"

    fun lire(contexte: Context): Int =
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getInt(CLE, 0)

    fun incrementer(contexte: Context) {
        val prefs = contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
        prefs.edit().putInt(CLE, prefs.getInt(CLE, 0) + 1).apply()
    }
}
