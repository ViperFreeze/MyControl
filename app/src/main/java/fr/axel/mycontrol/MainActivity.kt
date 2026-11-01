package fr.axel.mycontrol

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statut: TextView
    private lateinit var compteur: TextView
    private lateinit var boutonAccessibilite: Button

    private lateinit var statutProtection: TextView
    private lateinit var detailProtection: TextView
    private lateinit var boutonArmer: Button
    private lateinit var boutonDemander: Button
    private lateinit var boutonCode: Button
    private lateinit var boutonAnnuler: Button
    private lateinit var boutonReverrouiller: Button
    private lateinit var statutAdmin: TextView
    private lateinit var boutonAdmin: Button

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val admin by lazy { ComponentName(this, AdminReceiver::class.java) }

    private val handler = Handler(Looper.getMainLooper())
    private val tic = object : Runnable {
        override fun run() {
            rafraichir()
            handler.postDelayed(this, 1000)
        }
    }

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(40), px(24), px(32))
        }
        fun texte(t: String = "", taille: Float = 15f, gras: Boolean = false, haut: Int = 0) =
            TextView(this).apply {
                text = t
                textSize = taille
                if (gras) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, px(haut), 0, px(4))
            }.also { racine.addView(it) }
        fun bouton(libelle: String, action: () -> Unit) =
            Button(this).apply {
                text = libelle
                setOnClickListener { action() }
            }.also { racine.addView(it) }

        texte(getString(R.string.app_name), 30f, gras = true)
        texte(getString(R.string.sous_titre), haut = 4)

        // --- Blocage navigation privée ---
        texte(getString(R.string.section_blocage), 13f, gras = true, haut = 24)
        statut = texte(taille = 19f, gras = true)
        compteur = texte()
        boutonAccessibilite = bouton(getString(R.string.bouton_activer)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // --- Anti-désactivation ---
        texte(getString(R.string.section_protection), 13f, gras = true, haut = 28)
        statutProtection = texte(taille = 16f, gras = true)
        detailProtection = texte(taille = 14f)
        boutonArmer = bouton(getString(R.string.bouton_armer)) { armer() }
        boutonDemander = bouton(getString(R.string.bouton_demander)) { demander() }
        boutonCode = bouton(getString(R.string.bouton_entrer_code)) { entrerCode() }
        boutonAnnuler = bouton(getString(R.string.bouton_annuler)) {
            Securite.annulerDemande(this); rafraichir()
        }
        boutonReverrouiller = bouton(getString(R.string.bouton_reverrouiller)) {
            Securite.reverrouiller(this); toast(R.string.toast_reverrouille); rafraichir()
        }
        statutAdmin = texte(haut = 14)
        boutonAdmin = bouton(getString(R.string.bouton_admin)) { activerAdmin() }

        // --- Aide ---
        texte(getString(R.string.aide), 14f, haut = 28)
        texte(getString(R.string.navigateurs), 14f, haut = 16)

        setContentView(ScrollView(this).apply { addView(racine) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(tic)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tic)
    }

    private fun rafraichir() {
        // Blocage navigation privée
        val actif = serviceActif()
        statut.text = getString(if (actif) R.string.statut_actif else R.string.statut_inactif)
        statut.setTextColor(if (actif) VERT else ROUGE)
        boutonAccessibilite.text = getString(if (actif) R.string.bouton_gerer else R.string.bouton_activer)
        compteur.text = getString(R.string.compteur, Stats.lire(this))

        // Protection
        val protege = Securite.protege(this)
        val fenetre = Securite.deverrouilleRestantMs(this)
        val demande = Securite.demandeEnCours(this)
        val codeDispo = Securite.codeDisponible(this)

        statutProtection.text = when {
            !protege -> getString(R.string.protection_inactive)
            fenetre > 0 -> getString(R.string.protection_fenetre, (fenetre / 60_000) + 1)
            else -> getString(R.string.protection_active)
        }
        statutProtection.setTextColor(if (protege && fenetre == 0L) VERT else ROUGE)

        detailProtection.text = when {
            !protege -> getString(R.string.detail_inactive)
            fenetre > 0 -> getString(R.string.detail_fenetre)
            codeDispo -> getString(R.string.detail_code_pret)
            demande -> getString(R.string.detail_attente, Securite.attenteRestanteMs(this) / 60_000 + 1)
            else -> getString(R.string.detail_active)
        }

        boutonArmer.visibility = gone(!protege)
        boutonDemander.visibility = gone(protege && fenetre == 0L && !demande)
        boutonCode.visibility = gone(codeDispo && fenetre == 0L)
        boutonAnnuler.visibility = gone(demande && fenetre == 0L)
        boutonReverrouiller.visibility = gone(fenetre > 0)

        val adminActif = dpm.isAdminActive(admin)
        statutAdmin.text = getString(if (adminActif) R.string.admin_actif else R.string.admin_inactif)
        statutAdmin.setTextColor(if (adminActif) VERT else ROUGE)
        boutonAdmin.visibility = gone(!adminActif && (!protege || Securite.deverrouilleRestantMs(this) > 0))
    }

    private fun gone(visible: Boolean) = if (visible) View.VISIBLE else View.GONE

    // --- Actions ----------------------------------------------------------

    private fun armer() {
        if (!serviceActif()) { toast(R.string.toast_active_dabord); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialogue_armer_titre)
            .setMessage(R.string.dialogue_armer_message)
            .setPositiveButton(R.string.bouton_armer) { _, _ ->
                Securite.armer(this); toast(R.string.toast_armee); rafraichir()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun demander() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialogue_demander_titre)
            .setMessage(R.string.dialogue_demander_message)
            .setPositiveButton(R.string.bouton_demander) { _, _ ->
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
            setPadding(px(24), px(8), px(24), 0)
            addView(champ)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialogue_code_titre))
            .setMessage(getString(R.string.dialogue_code_message, code))
            .setView(conteneur)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (Securite.validerCode(this, champ.text.toString())) {
                    toast(R.string.toast_deverrouille)
                } else {
                    toast(R.string.toast_code_faux)
                }
                rafraichir()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun activerAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explication))
        )
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
        private val ROUGE = Color.rgb(198, 40, 40)
    }
}
