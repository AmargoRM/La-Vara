package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.needsUnlock

/**
 * Con el teléfono bloqueado, separa las acciones de una automatización en dos partes:
 * - [now]: las que no necesitan pantalla (volumen, música, linterna, notificación…), que corren ya;
 * - [afterUnlock]: las que abren algo o tocan botones, más las esperas que hay entre ellas, que corren al desbloquear.
 * Guarda los números de acción (empiezan en 0). Lógica pura.
 */
data class LockedSplit(val now: Set<Int>, val afterUnlock: Set<Int>) {

    companion object {
        /**
         * Devuelve null si no conviene separar y la automatización entera debe esperar el desbloqueo, como antes:
         * - si nada necesita desbloqueo (no hay nada que esperar);
         * - si lo que corre ya son solo notificaciones o esperas (así el aviso sale junto con la app);
         * - si tiene una espera larga, que sigue con una alarma y no se puede partir.
         */
        fun of(actions: List<Action>): LockedSplit? {
            val locked = actions.indices.filter { actions[it].needsUnlock() }
            if (locked.isEmpty()) return null
            if (actions.any { it is Action.Delay && it.seconds > AutomationEngine.INLINE_DELAY_SECONDS }) return null
            val first = locked.first()
            val last = locked.last()
            val afterUnlock = actions.indices.filter { i ->
                i in locked || (actions[i] is Action.Delay && i in first..last)
            }.toSet()
            val now = actions.indices.filter { !actions[it].needsUnlock() }.toSet()
            val useful = now.any { actions[it] !is Action.ShowNotification && actions[it] !is Action.Delay }
            return if (useful) LockedSplit(now, afterUnlock) else null
        }
    }
}
