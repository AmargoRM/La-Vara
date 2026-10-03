package com.lavara.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenUrlTest {

    private fun n(text: String) = Action.OpenUrl.normalize(text)

    @Test
    fun enlaceNormal_quedaIgual() {
        assertEquals("https://waze.com/ul?q=casa", n("https://waze.com/ul?q=casa"))
        assertEquals("http://ejemplo.com", n("  http://ejemplo.com  "))
    }

    @Test
    fun pegadoDetrasDelHttpsQueTraiaElCampo() {
        assertEquals("https://maps.app.goo.gl/AbC123", n("https://https://maps.app.goo.gl/AbC123"))
        assertEquals("https://maps.app.goo.gl/AbC123", n("https://maps.app.goo.gl/AbC123"))
    }

    @Test
    fun textoCompartido_tomaSoloElEnlace() {
        assertEquals("https://youtu.be/xyz", n("Mirá este video https://youtu.be/xyz que está bueno"))
        assertEquals("https://waze.com/ul?q=x", n("Vamos acá: https://waze.com/ul?q=x."))
    }

    @Test
    fun sinHttps_seAgrega() {
        assertEquals("https://waze.com/ul?q=x", n("waze.com/ul?q=x"))
        assertEquals("https://www.google.com", n("www.google.com"))
    }

    @Test
    fun mayusculas_enElEsquema() {
        assertEquals("https://Ejemplo.com/A", n("HTTPS://Ejemplo.com/A"))
    }

    @Test
    fun sinSitio_esNull() {
        assertNull(n(""))
        assertNull(n("https://"))
        assertNull(n("hola"))
        assertNull(n("https://localhost"))
        assertNull(n("correo@ejemplo.com"))
    }

    @Test
    fun host_toleraCaracteresRaros() {
        assertEquals("google.com", Action.OpenUrl("https://google.com/maps/place/Café Ñ|1").host)
        assertEquals("waze.com", Action.OpenUrl("https://WAZE.com:443/ul").host)
        assertEquals("enlace", Action.OpenUrl("https://").host)
    }
}
