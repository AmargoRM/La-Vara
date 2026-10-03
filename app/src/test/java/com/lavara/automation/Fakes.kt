package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.ActionExecutor
import com.lavara.actions.ActionResult
import com.lavara.core.Clock
import com.lavara.core.DeviceState
import java.time.ZoneId
import java.time.ZonedDateTime

/** Reloj de prueba que solo avanza cuando el test lo pide. */
class FakeClock(var current: ZonedDateTime = ZonedDateTime.of(2026, 10, 5, 8, 0, 0, 0, ZoneId.of("America/Costa_Rica"))) : Clock {
    override fun now(): ZonedDateTime = current
    fun advanceSeconds(seconds: Long) {
        current = current.plusSeconds(seconds)
    }
}

class FakeDevice(
    var battery: Int? = 50,
    var locked: Boolean = false,
    var charging: Boolean? = false,
    var wifi: Boolean? = false,
    var ssid: String? = null,
) : DeviceState {
    override fun batteryLevel(): Int? = battery
    override fun isLocked(): Boolean = locked
    override fun isCharging(): Boolean? = charging
    override fun isWifiConnected(): Boolean? = wifi
    override fun wifiSsid(): String? = ssid
}

/** Anota las acciones recibidas; falla las que estén en [failing]. */
class RecordingExecutor(private val failing: Set<Action> = emptySet()) : ActionExecutor {
    val done = mutableListOf<Action>()
    override suspend fun execute(action: Action): ActionResult {
        done += action
        return if (action in failing) ActionResult.Failure("falla de prueba") else ActionResult.Success
    }
}

class ListSource(var automations: List<Automation>) : AutomationSource {
    override suspend fun all(): List<Automation> = automations
    override suspend fun find(id: String): Automation? = automations.firstOrNull { it.id == id }
}
