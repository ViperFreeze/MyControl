package fr.axel.mycontrol

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mise à jour de MyControl par elle-même, sans désinstaller et sans code.
 *
 * Principe : GitHub Actions publie dans la Release « latest » l'APK et un
 * fichier version.txt (numéro de compilation). L'appli compare ce numéro à
 * sa propre version ; si une version plus récente existe, elle télécharge
 * l'APK et l'installe PAR-DESSUS elle-même avec le PackageInstaller d'Android.
 *
 * Pourquoi aucune désactivation n'est nécessaire : une mise à jour n'est pas
 * une désinstallation. Le service d'accessibilité, l'administrateur, l'état
 * verrouillé et les compteurs sont conservés. Android n'accepte l'APK que
 * s'il est signé avec la MÊME clé que l'appli installée : personne ne peut
 * faire passer une version modifiée par ce chemin.
 *
 * Confirmation : la toute première mise à jour faite par l'appli demande un
 * appui sur « Mettre à jour ». Ensuite (Android 12+), MyControl étant devenue
 * sa propre source d'installation, les mises à jour se font en silence, en
 * arrière-plan.
 */
object MajAppli {

    private const val BASE = "https://github.com/ViperFreeze/MyControl/releases/latest/download/"
    private const val URL_VERSION = BASE + "version.txt"
    private const val URL_APK = BASE + "MyControl.apk"

    private const val FICHIER = "maj_appli"
    private const val CLE_DISPO = "version_dispo"
    private const val CLE_DERNIER = "dernier_ms"
    private const val CLE_INSTALL = "installation_jusqua"

    /** Intervalle minimal entre deux vérifications automatiques. */
    private const val INTERVALLE_MS = 6 * 60 * 60 * 1000L // 6 h

    /** Pendant ce délai, les écrans d'installation ne sont pas bloqués. */
    private const val FENETRE_INSTALL_MS = 3 * 60_000L

    const val ACTION_RESULTAT = "fr.axel.mycontrol.RESULTAT_MAJ"

    enum class Etat { A_JOUR, DISPONIBLE, EN_COURS, ERREUR, INCONNU }

    @Volatile var etat: Etat = Etat.INCONNU
        private set
    @Volatile private var occupe = false

    private fun prefs(c: Context) = c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    // --- Versions ---------------------------------------------------------

    @Suppress("DEPRECATION")
    fun versionInstallee(c: Context): Long {
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    fun versionNom(c: Context): String =
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"

    /** Version disponible connue (0 = aucune plus récente). */
    fun versionDisponible(c: Context): Long {
        val v = prefs(c).getLong(CLE_DISPO, 0)
        return if (v > versionInstallee(c)) v else 0
    }

    private fun lireVersionDistante(): Long? {
        val conn = ouvrir(URL_VERSION)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            return conn.inputStream.bufferedReader().use { it.readText() }.trim().toLongOrNull()
        } finally {
            conn.disconnect()
        }
    }

    // --- Vérification -----------------------------------------------------

    /**
     * Vérifie s'il existe une nouvelle version, en arrière-plan.
     * [force] : ignore l'intervalle de 6 h (bouton « Rechercher »).
     * [installerSiSilencieux] : installe tout de suite si aucune confirmation
     * n'est nécessaire (cas des vérifications automatiques du service).
     */
    fun verifier(
        c: Context,
        force: Boolean = false,
        installerSiSilencieux: Boolean = true,
        fin: (() -> Unit)? = null,
    ) {
        val app = c.applicationContext
        if (!force &&
            System.currentTimeMillis() - prefs(app).getLong(CLE_DERNIER, 0) < INTERVALLE_MS
        ) {
            etat = if (versionDisponible(app) > 0) Etat.DISPONIBLE else Etat.A_JOUR
            fin?.let { Handler(Looper.getMainLooper()).post(it) }
            return
        }
        Thread {
            try {
                val distante = lireVersionDistante()
                if (distante == null) {
                    etat = Etat.ERREUR
                } else {
                    prefs(app).edit()
                        .putLong(CLE_DISPO, distante)
                        .putLong(CLE_DERNIER, System.currentTimeMillis())
                        .apply()
                    etat = if (distante > versionInstallee(app)) Etat.DISPONIBLE else Etat.A_JOUR
                    if (etat == Etat.DISPONIBLE && installerSiSilencieux && installationSilencieuse(app)) {
                        installer(app)
                    }
                }
            } catch (_: Exception) {
                etat = Etat.ERREUR
            }
            fin?.let { Handler(Looper.getMainLooper()).post(it) }
        }.apply { isDaemon = true }.start()
    }

    /** Vrai si Android peut installer la mise à jour sans demander de confirmation. */
    fun installationSilencieuse(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        if (!c.packageManager.canRequestPackageInstalls()) return false
        val source = try {
            c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName
        } catch (_: Exception) { null }
        return source == c.packageName
    }

    /** L'autorisation « Installer des applis inconnues » est-elle accordée ? */
    fun autorisee(c: Context) = c.packageManager.canRequestPackageInstalls()

    // --- Téléchargement et installation -----------------------------------

    /**
     * Télécharge l'APK puis lance l'installation. À appeler hors du thread
     * principal ou via [lancer].
     */
    fun installer(c: Context) {
        if (occupe) return
        occupe = true
        etat = Etat.EN_COURS
        try {
            val apk = File(c.cacheDir, "maj.apk")
            telecharger(apk)

            // Laisse passer les écrans d'installation pendant quelques minutes.
            prefs(c).edit()
                .putLong(CLE_INSTALL, System.currentTimeMillis() + FENETRE_INSTALL_MS)
                .apply()

            val installateur = c.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply {
                    setAppPackageName(c.packageName)
                    if (Build.VERSION.SDK_INT >= 31) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
            val id = installateur.createSession(params)
            installateur.openSession(id).use { session ->
                session.openWrite("MyControl.apk", 0, apk.length()).use { sortie ->
                    apk.inputStream().use { it.copyTo(sortie) }
                    session.fsync(sortie)
                }
                val retour = Intent(c, ResultatMaj::class.java).setAction(ACTION_RESULTAT)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(c, id, retour, flags)
                session.commit(pi.intentSender)
            }
        } catch (_: Exception) {
            etat = Etat.ERREUR
            occupe = false
        }
    }

    /** Lance [installer] en arrière-plan (depuis l'écran de l'appli). */
    fun lancer(c: Context, fin: (() -> Unit)? = null) {
        val app = c.applicationContext
        Thread {
            installer(app)
            fin?.let { Handler(Looper.getMainLooper()).post(it) }
        }.apply { isDaemon = true }.start()
    }

    private fun telecharger(cible: File) {
        val conn = ouvrir(URL_APK, lecture = 60_000)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            val temp = File(cible.parentFile, cible.name + ".part")
            conn.inputStream.use { entree -> temp.outputStream().use { entree.copyTo(it) } }
            if (temp.length() < 10_000) error("APK trop petit")
            temp.renameTo(cible)
        } finally {
            conn.disconnect()
        }
    }

    private fun ouvrir(url: String, lecture: Int = 10_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = lecture
            instanceFollowRedirects = true // GitHub redirige vers son stockage
            setRequestProperty("Cache-Control", "no-cache")
        }

    /** Une installation lancée par MyControl est-elle en cours ? */
    fun installationEnCours(c: Context) =
        System.currentTimeMillis() < prefs(c).getLong(CLE_INSTALL, 0)

    internal fun terminer(c: Context, ok: Boolean) {
        occupe = false
        etat = if (ok) Etat.A_JOUR else Etat.ERREUR
        prefs(c).edit().putLong(CLE_INSTALL, 0).apply()
    }

    /** Résultat renvoyé par Android à la fin (ou au milieu) de l'installation. */
    class ResultatMaj : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // Première mise à jour : Android demande une confirmation.
                    @Suppress("DEPRECATION")
                    val confirmer = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmer?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { c.startActivity(confirmer) } catch (_: Exception) { terminer(c, false) }
                }
                PackageInstaller.STATUS_SUCCESS -> terminer(c, true)
                else -> {
                    terminer(c, false)
                    Toast.makeText(c, R.string.maj_echec, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
