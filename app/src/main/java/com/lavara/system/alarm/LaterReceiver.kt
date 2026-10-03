package com.lavara.system.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lavara.LaVaraApp
import kotlinx.coroutines.launch

/** Terminó una espera larga: la automatización sigue donde había quedado. */
class LaterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val container = (context.applicationContext as LaVaraApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.laterAlarms.fire(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.lavara.SEGUIR_ESPERA"
        const val EXTRA_ID = "espera"
    }
}
