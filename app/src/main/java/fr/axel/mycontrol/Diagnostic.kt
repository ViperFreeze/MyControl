package fr.axel.mycontrol

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Mode diagnostic : pendant 10 minutes, MyControl note ce que les
 * navigateurs affichent (textes, identifiants, état sélectionné…) et chaque
 * blocage. Le rapport reste sur le téléphone ; il n'est partagé que si
 * l'utilisateur appuie sur « Partager le rapport ». Sert à ajuster la
 * détection quand un navigateur (ex. Opera GX) change ses écrans.
 *
 * Le blocage continue normalement pendant le diagnostic.
 */
object Diagnostic {
    private const val FICHIER = "diagnostic"
    private const val CLE_FIN = "fin"
    private const val CLE_RAPPORT = "rapport"
    private const val DUREE_MS = 10 * 60_000L
    private const val TAILLE_MAX = 60_000

    /** Copie en mémoire de l'échéance : lue à chaque événement, sans disque. */
    @Volatile private var fin = -1L

    private fun prefs(c: Context) = c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    fun actif(c: Context): Boolean {
        if (fin < 0) fin = prefs(c).getLong(CLE_FIN, 0L)
        return System.currentTimeMillis() < fin
    }

    fun activer(c: Context) {
        fin = System.currentTimeMillis() + DUREE_MS
        prefs(c).edit().putLong(CLE_FIN, fin).putString(CLE_RAPPORT, "").apply()
        noter(c, "Diagnostic démarré (10 min). Version ${versionAppli(c)}")
    }

    fun minutesRestantes(c: Context): Int =
        if (!actif(c)) 0 else (((fin - System.currentTimeMillis()) / 60_000L) + 1).toInt()

    fun rapport(c: Context): String = prefs(c).getString(CLE_RAPPORT, "") ?: ""

    @Synchronized
    fun noter(c: Context, ligne: String) {
        val heure = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
        var r = rapport(c) + "[$heure] $ligne\n"
        // On garde la fin du rapport (le plus récent) si ça devient trop long.
        if (r.length > TAILLE_MAX) r = r.takeLast(TAILLE_MAX)
        prefs(c).edit().putString(CLE_RAPPORT, r).apply()
    }

    /** Arbre de la fenêtre, une ligne par élément ayant du texte, un id ou un état. */
    fun decrireArbre(racine: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val file = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        file.add(racine to 0)
        var visites = 0
        while (file.isNotEmpty() && visites < 300) {
            val (n, prof) = file.poll() ?: break
            visites++
            val id = n.viewIdResourceName?.substringAfter(":id/")
            val texte = n.text?.toString()?.take(60)
            val desc = n.contentDescription?.toString()?.take(60)
            val etats = buildString {
                if (n.isSelected) append(" [sélectionné]")
                if (n.isChecked) append(" [coché]")
                if (n.isClickable) append(" [cliquable]")
            }
            if (id != null || texte != null || desc != null || n.isSelected || n.isChecked) {
                sb.append("  ".repeat(prof.coerceAtMost(12)))
                sb.append(n.className?.toString()?.substringAfterLast('.') ?: "?")
                id?.let { sb.append(" #").append(it) }
                texte?.let { sb.append(" «").append(it).append('»') }
                desc?.let { sb.append(" desc=«").append(it).append('»') }
                sb.append(etats).append('\n')
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { file.add(it to prof + 1) }
        }
        return sb.toString()
    }

    private fun versionAppli(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }
}
