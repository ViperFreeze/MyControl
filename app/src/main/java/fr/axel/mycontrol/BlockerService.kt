package fr.axel.mycontrol

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.ArrayDeque

/**
 * Service d'accessibilité qui surveille uniquement les navigateurs listés
 * dans res/xml/accessibility_config.xml.
 *
 * Deux protections :
 *  1. Clic intercepté : si l'utilisateur appuie sur un bouton/menu
 *     « Navigation privée », « Incognito », « InPrivate », « Mode secret »…
 *     on fait Retour immédiatement.
 *  2. Écran détecté : si un onglet privé est quand même affiché
 *     (page d'accueil privée, badge incognito…), on renvoie à l'écran d'accueil.
 */
class BlockerService : AccessibilityService() {

    private var dernierBlocageClic = 0L
    private var dernierBlocageEcran = 0L
    private var dernierScan = 0L

    private var dernierBlocageReglages = 0L
    private var dernierScanReglages = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Applique le cache et déclenche une mise à jour distante si besoin.
        MiseAJour.planifier(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val paquet = event.packageName?.toString() ?: return

        if (paquet in Detection.PAQUETS_REGLAGES) {
            surveillerReglages(event)
            return
        }
        if (paquet !in Detection.NAVIGATEURS) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                if (clicVersModePrive(event)) bloquerClic()

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val maintenant = SystemClock.elapsedRealtime()
                // Les changements de contenu sont très fréquents : on limite les scans
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    maintenant - dernierScan < 500
                ) return
                dernierScan = maintenant
                if (ecranPriveAffiche()) bloquerEcran()
            }
        }
    }

    private fun clicVersModePrive(event: AccessibilityEvent): Boolean {
        val morceaux = mutableListOf<CharSequence?>()
        morceaux.addAll(event.text)
        morceaux.add(event.contentDescription)
        event.source?.let { noeud ->
            morceaux.add(noeud.text)
            morceaux.add(noeud.contentDescription)
            morceaux.add(noeud.viewIdResourceName)
        }
        return morceaux.any { Detection.estClicPrive(it) }
    }

    private fun ecranPriveAffiche(): Boolean {
        val racine = rootInActiveWindow ?: return false
        if (racine.packageName?.toString() !in Detection.NAVIGATEURS) return false

        val file = ArrayDeque<AccessibilityNodeInfo>()
        file.add(racine)
        var visites = 0
        while (file.isNotEmpty() && visites < 600) {
            val noeud = file.poll() ?: break
            visites++
            if (Detection.estIdEcranPrive(noeud.viewIdResourceName) ||
                Detection.estPhraseEcranPrive(noeud.text) ||
                Detection.estPhraseEcranPrive(noeud.contentDescription)
            ) return true
            for (i in 0 until noeud.childCount) {
                noeud.getChild(i)?.let { file.add(it) }
            }
        }
        return false
    }

    private fun bloquerClic() {
        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant - dernierBlocageClic < 1200) return
        dernierBlocageClic = maintenant
        performGlobalAction(GLOBAL_ACTION_BACK)
        signaler()
    }

    private fun bloquerEcran() {
        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant - dernierBlocageEcran < 1500) return
        dernierBlocageEcran = maintenant
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        signaler()
    }

    // --- Anti-désactivation ------------------------------------------------

    private fun surveillerReglages(event: AccessibilityEvent) {
        if (!Securite.reglagesProteges(this)) return
        val maintenant = SystemClock.elapsedRealtime()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            maintenant - dernierScanReglages < 400
        ) return
        dernierScanReglages = maintenant

        val texte = StringBuilder()
        event.text.forEach { texte.append(it).append(' ') }
        texte.append(event.contentDescription ?: "").append(' ')
        rootInActiveWindow?.let { texte.append(texteDe(it)) }

        if (Detection.estEcranReglagesSensible(texte.toString())) bloquerReglages()
    }

    /** Concatène tout le texte visible d'une fenêtre. */
    private fun texteDe(racine: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val file = ArrayDeque<AccessibilityNodeInfo>()
        file.add(racine)
        var visites = 0
        while (file.isNotEmpty() && visites < 800) {
            val noeud = file.poll() ?: break
            visites++
            noeud.text?.let { sb.append(it).append(' ') }
            noeud.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until noeud.childCount) {
                noeud.getChild(i)?.let { file.add(it) }
            }
        }
        return sb.toString()
    }

    private fun bloquerReglages() {
        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant - dernierBlocageReglages < 1200) return
        dernierBlocageReglages = maintenant
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, R.string.toast_reglage_protege, Toast.LENGTH_LONG).show()
    }

    private fun signaler() {
        Stats.incrementer(this)
        Toast.makeText(this, R.string.toast_bloque, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
}
