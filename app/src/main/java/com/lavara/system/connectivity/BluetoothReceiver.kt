package com.lavara.system.connectivity

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lavara.LaVaraApp
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.launch

/**
 * Android avisa que un aparato Bluetooth se conectó o se desconectó. Este aviso llega aunque La Vara
 * esté cerrada (es una de las excepciones de Android 8), pero desde Android 12 solo con el permiso
 * "Dispositivos cercanos" (BLUETOOTH_CONNECT).
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> return
        }
        val device = deviceOf(intent) ?: return
        val container = (context.applicationContext as LaVaraApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                // Relojes y otros aparatos se conectan y desconectan seguido: sin automatizaciones de
                // Bluetooth activas no se registra nada, para no llenar el Historial.
                val triggers = container.automationRepository.all()
                    .filter { it.enabled }.mapNotNull { it.trigger as? Trigger.Bluetooth }
                if (!shouldHandle(BluetoothDevices.isBonded(device), device.address, triggers)) return@launch
                val name = BluetoothDevices.nameOf(device) ?: ""
                val now = container.clock.now().toInstant().toEpochMilli()
                container.automationRunner.handle(TriggerEvent.BluetoothChanged(device.address, name, connected, now))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * Celulares, televisores y relojes ajenos que pasan cerca abren conexiones cortas con el teléfono
         * (Compartir con Quick Share, la red de Encontrar mi dispositivo) y Android también las avisa. Solo
         * cuentan los aparatos vinculados a este teléfono, o uno que una automatización nombre a propósito;
         * los demás se ignoran sin escribir nada en el Historial.
         */
        fun shouldHandle(bonded: Boolean, address: String, activeTriggers: List<Trigger.Bluetooth>): Boolean {
            if (activeTriggers.isEmpty()) return false
            return bonded || activeTriggers.any { it.deviceAddress.equals(address, ignoreCase = true) }
        }
    }

    @Suppress("DEPRECATION")
    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
