package com.lavara.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemActionsTest {

    @Test
    fun accesoANoMolestar_soloParaNoMolestarYSilencio() {
        assertTrue(Action.DoNotDisturb(DndMode.OFF).needsDndAccess())
        assertTrue(Action.SetRingerMode(RingerMode.SILENT).needsDndAccess())
        assertFalse(Action.SetRingerMode(RingerMode.VIBRATE).needsDndAccess())
        assertFalse(Action.SetVolume().needsDndAccess())
        assertFalse(Action.Flashlight().needsDndAccess())
    }
}
