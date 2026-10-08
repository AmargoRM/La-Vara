package com.lavara.actions

import com.lavara.automation.AutomationDraft
import com.lavara.triggers.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapRecordingTest {

    private val uber = TapRecording("com.ubercab", "Uber")

    @Test
    fun grabaEnOrden_yCuentaLosToquesSinNombre() {
        val r = uber.add("Casa", 1_000).add(null, 2_000).add("Confirmar", 3_000)
        assertEquals(listOf("Casa", "Confirmar"), r.labels)
        assertEquals(1, r.skipped)
    }

    @Test
    fun mismoToqueAvisadoDosVeces_cuentaUnaVez() {
        val r = uber.add("Casa", 1_000).add("casa", 1_200)
        assertEquals(listOf("Casa"), r.labels)
        // El mismo botón tocado de nuevo un rato después sí cuenta.
        assertEquals(listOf("Casa", "Casa"), r.add("Casa", 3_000).labels)
    }

    @Test
    fun terminada_noGrabaMas_yTieneLimite() {
        assertEquals(emptyList<String>(), uber.copy(active = false).add("Casa", 1_000).labels)
        var r = uber
        repeat(TapRecording.MAX_STEPS + 5) { r = r.add("Botón $it", it * 10_000L) }
        assertEquals(TapRecording.MAX_STEPS, r.labels.size)
    }

    @Test
    fun quitarUnToque() {
        val r = uber.add("Casa", 1_000).add("Otro", 2_000).add("Confirmar", 3_000).remove(1)
        assertEquals(listOf("Casa", "Confirmar"), r.labels)
        assertEquals(r, r.remove(9))
    }

    @Test
    fun borrador_abreLaAppYTocaCadaBoton() {
        val draft = uber.add("Casa", 1_000).add("Confirmar", 2_000).toDraft()
        val wait = AutomationDraft.MAX_DELAY_SECONDS.toInt()
        assertEquals(Trigger.Manual, draft.trigger)
        assertEquals(
            listOf(
                Action.OpenApp("com.ubercab"),
                Action.TapInApp("com.ubercab", "Casa", wait),
                Action.TapInApp("com.ubercab", "Confirmar", wait),
            ),
            draft.actions,
        )
        assertTrue(draft.problems().isEmpty())
    }

    @Test
    fun mismoBotonSeguido_esperaUnSegundoEntreLosDos() {
        val actions = uber.add("Siguiente", 1_000).add("Siguiente", 5_000).toActions()
        assertEquals(Action.Delay(1), actions[2])
        assertEquals(4, actions.size)
    }
}
