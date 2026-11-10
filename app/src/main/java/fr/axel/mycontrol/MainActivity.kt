package fr.axel.mycontrol

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Écran unique, pensé comme un assistant : une seule chose à faire à la fois.
 * L'appli calcule où en est la configuration et n'affiche que l'étape utile.
 */
class MainActivity : Activity() {

    private enum class Phase {
        SERVICE_OFF,      // le blocage n'est pas activé
        ADMIN_OFF,        // blocage ok, désinstallation encore possible
        A_VERROUILLER,    // tout est prêt, reste à verrouiller
        ACTIVE,           // protection verrouillée, tout va bien
        ATTENTE,          // demande de désactivation lancée, compte à rebours
        CODE_PRET,        // le code est disponible
        FENETRE,          // déverrouillé : désinstallation possible
    }

    private lateinit var etiquetteEtape: TextView
    private lateinit var icone: TextView
    private lateinit var titre: TextView
    private lateinit var detail: TextView
    private lateinit var carteCompteur: TextView
    private lateinit var boutonPrincipal: Button
    private lateinit var boutonSecondaire: Button
    private lateinit var lienAide: TextView
    private lateinit var majStatut: TextView
    private lateinit var majBouton: Button

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val admin by lazy { ComponentName(this, AdminReceiver::class.java) }

    private val handler = Handler(Looper.getMainLooper())
    private val tic = object : Runnable {
        override fun run() { rafraichir(); handler.postDelayed(this, 1000) }
    }

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(44), px(24), px(28))
        }

        racine.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })
        racine.addView(TextView(this).apply {
            text = getString(R.string.sous_titre)
            textSize = 15f
            setTextColor(GRIS)
            setPadding(0, px(4), 0, px(24))
        })

        // --- Carte principale (l'étape en cours) ---
        val carte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22), px(22), px(22), px(22))
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(Color.rgb(245, 247, 250))
            }
        }
        etiquetteEtape = TextView(this).apply {
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.05f
            setTextColor(GRIS)
        }
        icone = TextView(this).apply {
            textSize = 40f
            setPadding(0, px(6), 0, px(6))
        }
        titre = TextView(this).apply {
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
        }
        detail = TextView(this).apply {
            textSize = 15f
            setTextColor(GRIS)
            setPadding(0, px(8), 0, px(18))
            setLineSpacing(px(3).toFloat(), 1f)
        }
        boutonPrincipal = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        boutonSecondaire = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(8) }
        }
        listOf(etiquetteEtape, icone, titre, detail, boutonPrincipal, boutonSecondaire)
            .forEach { carte.addView(it) }
        racine.addView(carte)

        // --- Mises à jour (sans désinstaller, sans code) ---
        val carteMaj = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22), px(18), px(22), px(18))
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(Color.rgb(245, 247, 250))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(16) }
        }
        carteMaj.addView(TextView(this).apply {
            text = getString(R.string.maj_section)
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.05f
            setTextColor(GRIS)
        })
        carteMaj.addView(TextView(this).apply {
            text = getString(R.string.maj_version, MajAppli.versionNom(this@MainActivity))
            textSize = 15f
            setPadding(0, px(6), 0, 0)
        })
        majStatut = TextView(this).apply {
            textSize = 14f
            setTextColor(GRIS)
            setPadding(0, px(4), 0, px(12))
            setLineSpacing(px(2).toFloat(), 1f)
        }
        majBouton = Button(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, WRAP) }
        carteMaj.addView(majStatut)
        carteMaj.addView(majBouton)
        racine.addView(carteMaj)

        // --- Compteur de blocages ---
        carteCompteur = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, px(22), 0, px(4))
        }
        racine.addView(carteCompteur)

        // --- Aide ---
        lienAide = TextView(this).apply {
            text = getString(R.string.lien_aide)
            textSize = 14f
            setTextColor(BLEU)
            gravity = Gravity.CENTER
            setPadding(0, px(18), 0, px(8))
            setOnClickListener { afficherAide() }
        }
        racine.addView(lienAide)

        setContentView(ScrollView(this).apply { addView(racine) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(tic)
        MajAppli.verifier(this) { rafraichir() }
    }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tic) }

    // --- Cœur : calcul de la phase et mise à jour de la carte --------------

    private fun phase(): Phase {
        if (!serviceActif()) return Phase.SERVICE_OFF
        if (!dpm.isAdminActive(admin)) return Phase.ADMIN_OFF
        if (!Securite.protege(this)) return Phase.A_VERROUILLER
        if (Securite.deverrouilleRestantMs(this) > 0) return Phase.FENETRE
        if (Securite.codeDisponible(this)) return Phase.CODE_PRET
        if (Securite.demandeEnCours(this)) return Phase.ATTENTE
        return Phase.ACTIVE
    }

    private fun rafraichir() {
        val p = phase()
        boutonSecondaire.visibility = View.GONE

        when (p) {
            Phase.SERVICE_OFF -> carte(
                "ÉTAPE 1 SUR 3", "🛡️", getString(R.string.e1_titre), getString(R.string.e1_detail),
                getString(R.string.e1_bouton)
            ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

            Phase.ADMIN_OFF -> carte(
                "ÉTAPE 2 SUR 3", "🔒", getString(R.string.e2_titre), getString(R.string.e2_detail),
                getString(R.string.e2_bouton)
            ) { activerAdmin() }

            Phase.A_VERROUILLER -> carte(
                "ÉTAPE 3 SUR 3", "✅", getString(R.string.e3_titre), getString(R.string.e3_detail),
                getString(R.string.e3_bouton)
            ) { armer() }

            Phase.ACTIVE -> {
                carte(
                    getString(R.string.actif_etiquette), "🟢", getString(R.string.actif_titre),
                    getString(R.string.actif_detail), getString(R.string.actif_bouton)
                ) { demander() }
                etiquetteEtape.setTextColor(VERT)
            }

            Phase.ATTENTE -> {
                val reste = Securite.attenteRestanteMs(this)
                val h = reste / 3_600_000
                val min = (reste % 3_600_000) / 60_000 + 1
                val dans = if (h > 0) getString(R.string.duree_h_min, h, min)
                           else getString(R.string.duree_min, min)
                carte(
                    getString(R.string.attente_etiquette), "⏳", getString(R.string.attente_titre),
                    getString(R.string.attente_detail, dans), null, null
                )
                secondaire(getString(R.string.bouton_annuler)) {
                    Securite.annulerDemande(this); rafraichir()
                }
            }

            Phase.CODE_PRET -> carte(
                getString(R.string.code_etiquette), "🔑", getString(R.string.code_titre),
                getString(R.string.code_detail), getString(R.string.bouton_entrer_code)
            ) { entrerCode() }

            Phase.FENETRE -> {
                val min = Securite.deverrouilleRestantMs(this) / 60_000 + 1
                carte(
                    getString(R.string.fenetre_etiquette), "🔓", getString(R.string.fenetre_titre),
                    getString(R.string.fenetre_detail, min), getString(R.string.fenetre_bouton)
                ) { desinstaller() }
                secondaire(getString(R.string.bouton_reverrouiller)) {
                    Securite.reverrouiller(this); toast(R.string.toast_reverrouille); rafraichir()
                }
            }
        }

        val actif = p != Phase.SERVICE_OFF
        carteCompteur.visibility = if (actif) View.VISIBLE else View.GONE
        carteCompteur.text = getString(R.string.compteur, Stats.lire(this))
        rafraichirMaj()
    }

    private fun rafraichirMaj() {
        val dispo = MajAppli.versionDisponible(this)
        when {
            MajAppli.etat == MajAppli.Etat.EN_COURS -> {
                majStatut.text = getString(R.string.maj_en_cours)
                majBouton.visibility = View.GONE
            }
            dispo > 0 && !MajAppli.autorisee(this) -> {
                majStatut.text = getString(R.string.maj_autoriser)
                bouton(majBouton, getString(R.string.maj_bouton_autoriser)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:$packageName")
                        )
                    )
                }
            }
            dispo > 0 -> {
                majStatut.text = getString(R.string.maj_disponible, dispo)
                bouton(majBouton, getString(R.string.maj_bouton_installer)) {
                    MajAppli.lancer(this) { rafraichir() }
                    rafraichir()
                }
            }
            else -> {
                majStatut.text = getString(
                    when (MajAppli.etat) {
                        MajAppli.Etat.A_JOUR -> R.string.maj_a_jour
                        MajAppli.Etat.ERREUR -> R.string.maj_erreur
                        else -> R.string.maj_inconnu
                    }
                )
                bouton(majBouton, getString(R.string.maj_bouton_chercher)) {
                    MajAppli.verifier(this, force = true, installerSiSilencieux = false) { rafraichir() }
                }
            }
        }
    }

    private fun bouton(b: Button, libelle: String, action: () -> Unit) {
        b.visibility = View.VISIBLE
        b.text = libelle
        b.setOnClickListener { action() }
    }

    private fun carte(
        etiquette: String, emoji: String, t: String, d: String,
        bouton: String?, action: (() -> Unit)? = null
    ) {
        etiquetteEtape.text = etiquette
        etiquetteEtape.setTextColor(GRIS)
        icone.text = emoji
        titre.text = t
        detail.text = d
        if (bouton != null) {
            boutonPrincipal.visibility = View.VISIBLE
            boutonPrincipal.text = bouton
            boutonPrincipal.setOnClickListener { action?.invoke() }
        } else {
            boutonPrincipal.visibility = View.GONE
        }
    }

    private fun secondaire(libelle: String, action: () -> Unit) {
        boutonSecondaire.visibility = View.VISIBLE
        boutonSecondaire.text = libelle
        boutonSecondaire.setOnClickListener { action() }
    }

    // --- Actions ----------------------------------------------------------

    private fun activerAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explication))
        )
    }

    private fun armer() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialogue_armer_titre)
            .setMessage(R.string.dialogue_armer_message)
            .setPositiveButton(R.string.e3_bouton) { _, _ ->
                Securite.armer(this); toast(R.string.toast_armee); rafraichir()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun demander() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialogue_demander_titre)
            .setMessage(R.string.dialogue_demander_message)
            .setPositiveButton(R.string.actif_bouton) { _, _ ->
                Securite.demanderDesactivation(this); rafraichir()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun entrerCode() {
        val code = Securite.code(this) ?: run { rafraichir(); return }
        val champ = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.indice_code)
        }
        val conteneur = FrameLayout(this).apply {
            setPadding(px(24), px(8), px(24), 0); addView(champ)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialogue_code_titre))
            .setMessage(getString(R.string.dialogue_code_message, code))
            .setView(conteneur)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (Securite.validerCode(this, champ.text.toString())) toast(R.string.toast_deverrouille)
                else toast(R.string.toast_code_faux)
                rafraichir()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Pendant la fenêtre : on retire l'admin puis on ouvre la désinstallation. */
    private fun desinstaller() {
        if (dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin)
        startActivity(
            Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))
        )
    }

    private fun afficherAide() {
        AlertDialog.Builder(this)
            .setTitle(R.string.lien_aide)
            .setMessage(getString(R.string.aide))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // --- Utilitaires ------------------------------------------------------

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()

    private fun serviceActif(): Boolean {
        val attendu = ComponentName(this, BlockerService::class.java)
        val actifs = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return actifs.split(':').any { ComponentName.unflattenFromString(it) == attendu }
    }

    companion object {
        private val VERT = Color.rgb(46, 125, 50)
        private val GRIS = Color.rgb(110, 118, 130)
        private val BLEU = Color.rgb(25, 103, 210)
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
