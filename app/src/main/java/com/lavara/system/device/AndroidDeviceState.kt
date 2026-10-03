package com.lavara.system.device

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.BatteryManager
import com.lavara.core.DeviceState

/** Estado real del teléfono para el motor. */
class AndroidDeviceState(context: Context) : DeviceState {
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    override fun isLocked(): Boolean = keyguard.isKeyguardLocked || !power.isInteractive

    override fun batteryLevel(): Int? =
        battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
}
