package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.conditions.Comparison
import com.lavara.conditions.Condition
import com.lavara.triggers.BatteryDirection
import com.lavara.triggers.PowerEvent
import com.lavara.triggers.Trigger

/** Automatizaciones que La Vara crea sola la primera vez, desactivadas, como ejemplo. */
object Templates {
    const val PRUEBA_VARA_ID = "prueba-vara"
    const val BATERIA_BAJA_ID = "bateria-baja"
    const val CARGADOR_ID = "cargador-conectado"

    /** El criterio de éxito del MVP: CUANDO 08:00, SI batería > 20 %, HACER mostrar notificación. */
    fun pruebaVara(now: Long) = Automation(
        id = PRUEBA_VARA_ID,
        name = "Prueba Vara",
        description = "Automatización de ejemplo. Activala y cambiá la hora para probar que las alarmas llegan.",
        enabled = false,
        trigger = Trigger.Time("08:00"),
        conditions = listOf(Condition.BatteryLevel(Comparison.GREATER_THAN, 20)),
        actions = listOf(
            Action.ShowNotification(
                title = "Prueba Vara",
                text = "Funciona: batería %battery % a las %time del %date.",
            ),
        ),
        createdAt = now,
        updatedAt = now,
    )

    /** Aviso cuando la batería baja al 20 %. */
    fun bateriaBaja(now: Long) = Automation(
        id = BATERIA_BAJA_ID,
        name = "Batería baja",
        description = "Ejemplo. Avisa una vez cuando la batería baja al 20 %. Cambiá el porcentaje en el editor y activala.",
        enabled = false,
        trigger = Trigger.Battery(threshold = 20, direction = BatteryDirection.BELOW),
        actions = listOf(Action.ShowNotification(title = "Batería baja", text = "Queda %battery % (a las %time).")),
        createdAt = now,
        updatedAt = now,
    )

    /** Aviso al enchufar el cargador. */
    fun cargadorConectado(now: Long) = Automation(
        id = CARGADOR_ID,
        name = "Cargador conectado",
        description = "Ejemplo. Avisa al enchufar el cargador. Cambiá la acción en el editor y activala.",
        enabled = false,
        trigger = Trigger.Power(PowerEvent.CONNECTED),
        actions = listOf(Action.ShowNotification(title = "Cargando", text = "Cargador conectado con %battery %.")),
        createdAt = now,
        updatedAt = now,
    )

    /** Grupos de ejemplos: cada uno se crea una sola vez, según la clave guardada en settings. */
    fun groups(): List<Pair<String, (Long) -> List<Automation>>> = listOf(
        "plantillas_creadas" to { now -> listOf(pruebaVara(now)) },
        "plantillas_bateria" to { now -> listOf(bateriaBaja(now), cargadorConectado(now)) },
    )
}
