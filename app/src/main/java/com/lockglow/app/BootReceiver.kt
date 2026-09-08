package com.lockglow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Restarts protection after reboot - but only if setup was already completed
 * once (overlay permission + a fingerprint enrolled). Otherwise we'd try to
 * draw an overlay we don't have permission for.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (Settings.canDrawOverlays(context)) {
            LockOverlayService.start(context)
        }
    }
}
