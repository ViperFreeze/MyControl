package fr.axel.mycontrol

import android.accessibilityservice.AccessibilityService
import android.app.Notification
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
 *
 * Et un blocage total : les applis de Detection.APPLIS_BLOQUEES (Samsung
 * Internet) sont refermées dès qu'elles s'affichent, y compris quand une autre
 * appli ouvre un lien dedans.
 */
class BlockerService : AccessibilityService() {

    private var dernierBlocageClic = 0L
    private var dernierBlocageEcran = 0L
    private var dernierScan = 0L

    private var dernierBlocageAppli = 0L
    private var dernierCheckMaj = 0L

    private var dernierBlocageReglages = 0L
    private var dernierScanReglages = 0L

    /** Moment où un raccourci « Nouvel onglet privé » a été touché (lanceur…). */
    private var lancementPriveDemande = 0L
    private var dernierBlocageNotif = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Applique le cache et déclenche une mise à jour distante si besoin.
        MiseAJour.planifier(this)
        // Vérifie si une nouvelle version de l'appli existe (et l'installe
        // en silence quand Android le permet).
        MajAppli.verifier(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val paquet = event.packageName?.toString() ?: return

        // Le service tourne en continu : on en profite pour vérifier les mises
        // à jour de temps en temps (MajAppli limite lui-même à une fois / 6 h).
        val t = SystemClock.elapsedRealtime()
        if (t - dernierCheckMaj > 60 * 60_000L) {
            dernierCheckMaj = t
            MajAppli.verifier(this)
        }

        if (paquet in Detection.PAQUETS_REGLAGES) {
            surveillerReglages(event)
            return
        }
        if (paquet in Detection.APPLIS_BLOQUEES) {
            surveillerAppliBloquee(event, paquet)
            return
        }
        if (paquet !in Detection.NAVIGATEURS) {
            // Raccourci de l'icône (appui long → « Nouvel onglet privé ») :
            // le navigateur s'ouvrira directement en privé, on le referme dès
            // qu'il apparaît.
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                paquet != packageName && clicLancementPrive(event)
            ) {
                lancementPriveDemande = SystemClock.elapsedRealtime()
            }
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            surveillerNotification(event)
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val maintenant = SystemClock.elapsedRealtime()
            if (maintenant - lancementPriveDemande < 4000) {
                lancementPriveDemande = 0L
                bloquerEcran()
                return
            }
            if (Detection.estActivitePrivee(event.className)) {
                bloquerEcran()
                return
            }
        }

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

    private fun clicLancementPrive(event: AccessibilityEvent): Boolean {
        val morceaux = mutableListOf<CharSequence?>()
        morceaux.addAll(event.text)
        morceaux.add(event.contentDescription)
        event.source?.let { noeud ->
            morceaux.add(noeud.text)
            morceaux.add(noeud.contentDescription)
        }
        return morceaux.any { Detection.estLancementPrive(it) }
    }

    /**
     * Brave (comme Chrome, Edge, Vivaldi…) affiche une notification
     * « Fermer tous les onglets privés » dès qu'un onglet privé existe, même
     * en arrière-plan. On déclenche son action : tous les onglets privés sont
     * fermés d'un coup, quel que soit le chemin utilisé pour les ouvrir.
     */
    private fun surveillerNotification(event: AccessibilityEvent) {
        val notif = event.parcelableData as? Notification
        val texte = StringBuilder()
        event.text.forEach { texte.append(it).append(' ') }
        notif?.extras?.let { extras ->
            texte.append(extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").append(' ')
            texte.append(extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").append(' ')
        }
        notif?.actions?.forEach { texte.append(it.title ?: "").append(' ') }
        if (!Detection.estNotifOngletsPrives(texte)) return

        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant - dernierBlocageNotif < 1500) return
        dernierBlocageNotif = maintenant

        // L'intent principal de cette notification ferme les onglets privés ;
        // on tente aussi le bouton d'action s'il y en a un.
        val intents = buildList {
            notif?.contentIntent?.let { add(it) }
            notif?.actions?.forEach { a ->
                if (Detection.estNotifOngletsPrives(a.title)) a.actionIntent?.let { add(it) }
            }
        }
        var ferme = false
        for (pi in intents) {
            try {
                pi.send()
                ferme = true
                break
            } catch (_: Exception) {
                // PendingIntent annulé ou refusé : on essaie le suivant.
            }
        }
        // Si le navigateur est à l'écran, on en sort aussi.
        val auPremierPlan = rootInActiveWindow?.packageName?.toString()
        if (auPremierPlan in Detection.NAVIGATEURS) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            ferme = true
        }
        if (ferme) signaler()
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

    // --- Blocage total (Samsung Internet) ----------------------------------

    private fun surveillerAppliBloquee(event: AccessibilityEvent, paquet: String) {
        when (event.eventType) {
            // Ouverture d'une fenêtre de l'appli : blocage immédiat.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> bloquerAppli()
            // Changement de contenu : on vérifie que l'appli est bien au
            // premier plan (pas une simple mise à jour en arrière-plan).
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val auPremierPlan = rootInActiveWindow?.packageName?.toString()
                if (auPremierPlan == paquet) bloquerAppli()
            }
        }
    }

    private fun bloquerAppli() {
        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant - dernierBlocageAppli < 1500) return
        dernierBlocageAppli = maintenant
        performGlobalAction(GLOBAL_ACTION_HOME)
        Stats.incrementer(this)
        Toast.makeText(this, R.string.toast_appli_bloquee, Toast.LENGTH_SHORT).show()
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

        val contenu = texte.toString()

        // Mise à jour lancée par MyControl : la fenêtre « Mettre à jour cette
        // appli ? » doit rester accessible. Une désinstallation, elle, reste
        // toujours bloquée.
        if (event.packageName?.toString() in Detection.PAQUETS_INSTALLATEUR &&
            MajAppli.installationEnCours(this) &&
            !Detection.parleDeDesinstallation(contenu)
        ) return

        if (Detection.estEcranReglagesSensible(contenu)) bloquerReglages()
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
