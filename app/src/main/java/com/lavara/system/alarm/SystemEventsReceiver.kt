package com.lavara.system.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lavara.LaVaraApp
import kotlinx.coroutines.launch

/**
 * Android borra las alarmas al reiniciar y puede desfasarlas si cambia la hora o la zona.
 * Ante cualquiera de estos eventos se vuelve a programar todo.
 */
class SystemEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "teléfono reiniciado"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "La Vara actualizada"
            Intent.ACTION_TIME_CHANGED -> "cambió la hora del teléfono"
            Intent.ACTION_TIMEZONE_CHANGED -> "cambió la zona horaria"
            else -> return
        }
        val container = (context.applicationContext as LaVaraApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.logger.info("Sistema", "Evento del sistema: $reason")
                container.refreshTriggers(reason)
            } finally {
                pending.finish()
            }
        }
    }
}
