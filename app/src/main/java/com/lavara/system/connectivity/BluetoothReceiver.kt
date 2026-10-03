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
                if (container.automationRepository.all().none { it.enabled && it.trigger is Trigger.Bluetooth }) return@launch
                val name = BluetoothDevices.nameOf(device) ?: ""
                val now = container.clock.now().toInstant().toEpochMilli()
                container.automationRunner.handle(TriggerEvent.BluetoothChanged(device.address, name, connected, now))
            } finally {
                pending.finish()
            }
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
