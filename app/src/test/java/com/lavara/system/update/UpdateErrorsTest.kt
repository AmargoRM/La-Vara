package com.lavara.system.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpdateErrorsTest {

    @Test
    fun cadaCodigoTieneMensajeEnEspanol() {
        assertTrue(UpdateErrors.forHttpCode(401).contains("vencido"))
        assertTrue(UpdateErrors.forHttpCode(403).contains("permiso"))
        assertTrue(UpdateErrors.forHttpCode(404).contains("ultima"))
        assertTrue(UpdateErrors.forHttpCode(503).contains("503"))
        assertTrue(UpdateErrors.forHttpCode(418).contains("418"))
    }

    @Test
    fun vencimientoAceptaZonaConNombreOConDesfase() {
        assertEquals(LocalDate.of(2027, 10, 2), TokenExpiry.parse("2027-10-02 12:00:00 UTC"))
        assertEquals(LocalDate.of(2027, 10, 2), TokenExpiry.parse("2027-10-02 12:00:00 -0600"))
        assertNull(TokenExpiry.parse(null))
        assertNull(TokenExpiry.parse("basura"))
    }

    @Test
    fun avisaDosSemanasAntesDeVencer() {
        val hoy = LocalDate.of(2027, 1, 1)
        assertFalse(TokenExpiry.shouldWarn(LocalDate.of(2027, 1, 16), hoy))
        assertTrue(TokenExpiry.shouldWarn(LocalDate.of(2027, 1, 15), hoy))
        assertTrue(TokenExpiry.shouldWarn(LocalDate.of(2026, 12, 31), hoy))
    }
}
