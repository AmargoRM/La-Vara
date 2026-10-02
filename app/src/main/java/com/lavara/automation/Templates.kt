package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.conditions.Comparison
import com.lavara.conditions.Condition
import com.lavara.triggers.Trigger

/** Automatizaciones que La Vara crea sola la primera vez, desactivadas, como ejemplo. */
object Templates {
    const val PRUEBA_VARA_ID = "prueba-vara"

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
}
