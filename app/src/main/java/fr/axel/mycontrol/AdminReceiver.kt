package fr.axel.mycontrol

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Administrateur de l'appareil : tant qu'il est actif, MyControl ne peut pas
 * être désinstallée. Le désactiver passe par un écran des réglages que le
 * service bloque sans le code PIN.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_avertissement)
}
