package com.wifichat.mesh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wifichat.mesh.MeshApplication
import com.wifichat.mesh.util.MeshLog

/**
 * Restarts the mesh service after a reboot so a user who left discovery enabled
 * does not have to reopen the app. This is best-effort: some OEM ROMs block it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? MeshApplication ?: return
        if (!app.container.settings.discoveryEnabled.value) return
        try {
            MeshService.start(context)
        } catch (t: Throwable) {
            MeshLog.w("Boot restart failed: ${t.message}")
        }
    }
}
