package com.lavara.system.device

import android.content.Context
import android.os.BatteryManager
import com.lavara.core.DeviceState

/** Estado real del teléfono para el motor. */
class AndroidDeviceState(context: Context) : DeviceState {
    private val battery = context.getSystemService(BatteryManager::class.java)

    override fun batteryLevel(): Int? =
        battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
}
