package com.eymen.os

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Opens EYMEN OS when the TV starts. eski ana ekran opens its own home screen a few seconds
 * after start-up, so the launcher is brought to front again during the first seconds.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(LauncherState.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("autoStart", true)) return
        val app = context.applicationContext
        open(app)
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ open(app) }, 3500L)
        handler.postDelayed({
            open(app)
            try {
                pending.finish()
            } catch (e: Exception) {
            }
        }, 8000L)
    }

    private fun open(context: Context) {
        try {
            val i = Intent(context, HomeActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(i)
        } catch (e: Exception) {
        }
    }
}
