package com.lavara.system.device

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lavara.data.AutomationRepository
import com.lavara.data.SettingsRepository
import com.lavara.logging.AppLogger
import com.lavara.system.AutomationRunner
import com.lavara.triggers.BatteryReading
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import com.lavara.triggers.TriggerMatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Enciende o apaga [DeviceWatchService] según haga falta y convierte lo que avisa Android sobre la
 * batería y el cargador en eventos para el motor.
 *
 * Desde Android 8, una app cerrada no recibe los avisos de batería ni de cargador (ver
 * docs/LIMITES_ANDROID.md). Por eso, solo mientras haya una automatización activa con esos
 * disparadores, La Vara mantiene un servicio con la notificación "La Vara está activa".
 */
class DeviceWatcher(
    private val context: Context,
    private val automations: AutomationRepository,
    private val settings: SettingsRepository,
    private val logger: AppLogger,
    private val runner: () -> AutomationRunner,
) {
    private val mutex = Mutex()

    /** Último porcentaje visto en memoria: Android repite el aviso de batería muchas veces sin cambio. */
    @Volatile private var lastLevel: Int? = null

    /** Revisa las automatizaciones y enciende o apaga la vigilancia. [reason] queda en el registro. */
    suspend fun sync(reason: String) {
        val needed = TriggerMatcher.needsDeviceWatch(automations.all())
        val intent = Intent(context, DeviceWatchService::class.java)
        if (needed && !DeviceWatchService.isRunning) {
            try {
                ContextCompat.startForegroundService(context, intent)
                logger.info(SOURCE, "Vigilancia de batería, cargador y Wi-Fi encendida ($reason)")
            } catch (e: IllegalStateException) {
                // Android 12+ no deja encenderla con La Vara en segundo plano, salvo excepciones
                // (reinicio del teléfono, app sin ahorro de batería). Se reintenta al abrir La Vara.
                logger.warn(
                    SOURCE,
                    "Android no dejó encender la vigilancia de batería y cargador ($reason): ${e.message}. " +
                        "Abrí La Vara para encenderla, y quitá el ahorro de batería para que no vuelva a pasar.",
                )
            }
        } else if (!needed && DeviceWatchService.isRunning) {
            context.stopService(intent)
            settings.set(KEY_LAST_LEVEL, "")
            lastLevel = null
            logger.info(SOURCE, "Vigilancia de batería, cargador y Wi-Fi apagada: ninguna automatización activa la usa ($reason)")
        }
    }

    /**
     * Llega una lectura de batería. Solo pasa al motor si cruza el umbral de alguna automatización:
     * el porcentaje cambia unas cien veces por día y llenaría los registros.
     */
    suspend fun onBatteryLevel(level: Int): Unit = mutex.withLock {
        // El aviso llega cada vez que cambia la temperatura o el voltaje; sin cambio de porcentaje no se lee la base.
        if (lastLevel == level) return@withLock
        lastLevel = level
        val previous = settings.get(KEY_LAST_LEVEL)?.toIntOrNull()
        if (previous == level) return@withLock
        settings.set(KEY_LAST_LEVEL, level.toString())
        if (previous == null) {
            logger.info(SOURCE, "Batería ahora: $level %. Se avisa cuando cruce el umbral de una automatización.")
        }
        val event = BatteryReading.eventFor(previous, level) ?: return@withLock
        val all = automations.all()
        val crosses = all.any { it.enabled && it.trigger is Trigger.Battery && TriggerMatcher.matches(it.trigger, it.id, event) }
        if (crosses) runner().handle(event)
        Unit
    }

    suspend fun onPower(connected: Boolean, atMillis: Long) {
        runner().handle(TriggerEvent.PowerChanged(connected, atMillis))
    }

    /** Cambió el Wi-Fi. Solo pasa al motor si hay automatizaciones de Wi-Fi activas, para no llenar los registros. */
    suspend fun onWifi(ssid: String?, connected: Boolean, atMillis: Long) {
        val wifi = automations.all().filter { it.enabled && it.trigger is Trigger.Wifi }
        if (wifi.isEmpty()) return
        if (ssid == null && wifi.any { (it.trigger as Trigger.Wifi).ssid.isNotBlank() }) {
            logger.warn(
                "Wi-Fi",
                "Android no dijo el nombre de la red. Para reconocer redes por nombre, La Vara necesita la ubicación " +
                    "\"Permitir todo el tiempo\" y la ubicación del teléfono encendida.",
            )
        }
        runner().handle(TriggerEvent.WifiChanged(ssid, connected, atMillis))
    }

    private companion object {
        const val SOURCE = "Batería"

        /** Último porcentaje visto, para saber si cruzó un umbral aunque Android haya cerrado La Vara en el medio. */
        const val KEY_LAST_LEVEL = "bateria_ultimo_nivel"
    }
}
