package fr.axel.mycontrol

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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

    private lateinit var statutPin: TextView
    private lateinit var boutonPin: Button
    private lateinit var boutonDeverrouiller: Button
    private lateinit var boutonReverrouiller: Button
    private lateinit var statutAdmin: TextView
    private lateinit var boutonAdmin: Button

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val admin by lazy { ComponentName(this, AdminReceiver::class.java) }

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
        fun bouton(action: () -> Unit) =
            Button(this).apply { setOnClickListener { action() } }.also { racine.addView(it) }

        texte(getString(R.string.app_name), 30f, gras = true)
        texte(getString(R.string.sous_titre), haut = 4)

        // --- Blocage navigation privée ---
        texte(getString(R.string.section_blocage), 13f, gras = true, haut = 24)
        statut = texte(taille = 19f, gras = true)
        compteur = texte()
        boutonAccessibilite = bouton { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        // --- Anti-désactivation ---
        texte(getString(R.string.section_protection), 13f, gras = true, haut = 28)
        statutPin = texte(taille = 16f, gras = true)
        boutonPin = bouton { changerPin() }
        boutonDeverrouiller = bouton { deverrouiller() }
        boutonReverrouiller = bouton {
            Securite.verrouiller(this)
            toast(R.string.toast_reverrouille)
            rafraichir()
        }.apply { text = getString(R.string.bouton_reverrouiller) }
        statutAdmin = texte(haut = 12)
        boutonAdmin = bouton { activerAdmin() }.apply { text = getString(R.string.bouton_admin) }

        // --- Aide ---
        texte(getString(R.string.aide), 14f, haut = 28)
        texte(getString(R.string.navigateurs), 14f, haut = 16)

        setContentView(ScrollView(this).apply { addView(racine) })
    }

    override fun onResume() {
        super.onResume()
        rafraichir()
    }

    private fun rafraichir() {
        val actif = serviceActif()
        statut.text = getString(if (actif) R.string.statut_actif else R.string.statut_inactif)
        statut.setTextColor(if (actif) VERT else ROUGE)
        boutonAccessibilite.text = getString(if (actif) R.string.bouton_gerer else R.string.bouton_activer)
        compteur.text = getString(R.string.compteur, Stats.lire(this))

        val pin = Securite.pinDefini(this)
        val restant = Securite.deverrouilleRestantMs(this)
        statutPin.text = when {
            !pin -> getString(R.string.pin_absent)
            restant > 0 -> getString(R.string.pin_deverrouille, (restant / 60_000) + 1)
            else -> getString(R.string.pin_actif)
        }
        statutPin.setTextColor(if (pin && restant == 0L) VERT else ROUGE)
        boutonPin.text = getString(if (pin) R.string.bouton_changer_pin else R.string.bouton_definir_pin)
        boutonDeverrouiller.visibility = if (pin && restant == 0L) View.VISIBLE else View.GONE
        boutonDeverrouiller.text = getString(R.string.bouton_deverrouiller)
        boutonReverrouiller.visibility = if (pin && restant > 0) View.VISIBLE else View.GONE

        val adminActif = dpm.isAdminActive(admin)
        statutAdmin.text = getString(if (adminActif) R.string.admin_actif else R.string.admin_inactif)
        statutAdmin.setTextColor(if (adminActif) VERT else ROUGE)
        boutonAdmin.visibility = if (adminActif) View.GONE else View.VISIBLE
    }

    // --- PIN ---------------------------------------------------------------

    private fun changerPin() {
        if (Securite.pinDefini(this)) {
            avecPinVerifie { nouveauPin() }
        } else {
            nouveauPin()
        }
    }

    private fun nouveauPin() {
        demanderPin(getString(R.string.dialogue_nouveau_pin)) { p1 ->
            if (!Securite.pinValide(p1)) {
                toast(R.string.toast_pin_invalide); return@demanderPin
            }
            demanderPin(getString(R.string.dialogue_confirmer_pin)) { p2 ->
                if (p1 == p2) {
                    Securite.definirPin(this, p1)
                    Securite.verrouiller(this)
                    toast(R.string.toast_pin_enregistre)
                } else {
                    toast(R.string.toast_pin_different)
                }
                rafraichir()
            }
        }
    }

    private fun deverrouiller() {
        avecPinVerifie {
            Securite.deverrouiller(this)
            toast(R.string.toast_deverrouille)
            rafraichir()
        }
    }

    /** Activer l'administrateur ouvre un écran des réglages : on le laisse passer 2 min. */
    private fun activerAdmin() {
        val lancer = {
            if (Securite.pinDefini(this)) Securite.deverrouiller(this, 2 * 60_000L)
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explication))
            )
        }
        if (Securite.pinDefini(this)) avecPinVerifie(lancer) else lancer()
    }

    private fun avecPinVerifie(action: () -> Unit) {
        val attente = Securite.attenteRestanteMs(this)
        if (attente > 0) {
            Toast.makeText(this, getString(R.string.toast_attente, attente / 1000 + 1), Toast.LENGTH_SHORT).show()
            return
        }
        demanderPin(getString(R.string.dialogue_pin_actuel)) { pin ->
            if (Securite.verifier(this, pin)) action() else toast(R.string.toast_pin_faux)
        }
    }

    private fun demanderPin(titre: String, surValidation: (String) -> Unit) {
        val champ = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.indice_pin)
        }
        val conteneur = FrameLayout(this).apply {
            setPadding(px(24), px(8), px(24), 0)
            addView(champ)
        }
        AlertDialog.Builder(this)
            .setTitle(titre)
            .setView(conteneur)
            .setPositiveButton(android.R.string.ok) { _, _ -> surValidation(champ.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- Utilitaires ---------------------------------------------------------

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
