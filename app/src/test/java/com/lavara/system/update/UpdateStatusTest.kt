package com.lavara.system.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class UpdateStatusTest {

    private val release91 = ReleaseInfo(91, "0.1.91", "https://api.github.com/x", 1L)
    private val guardado = UpdateStatus(1L, "Hay una versión nueva: 0.1.91.", release91, null)

    @Test
    fun yaInstalada_noSeOfreceDescargarla() {
        val visto = guardado.forInstalled(91, "0.1.91")
        assertNull(visto.available)
        assertEquals("Tenés la última versión (0.1.91).", visto.lastMessage)
        assertNull(guardado.forInstalled(95, "0.1.95").available)
    }

    @Test
    fun masNueva_seSigueOfreciendo() {
        assertSame(guardado, guardado.forInstalled(86, "0.1.86"))
        val sinVersion = UpdateStatus(1L, "Tenés la última versión (0.1.86).", null, null)
        assertSame(sinVersion, sinVersion.forInstalled(91, "0.1.91"))
    }
}
