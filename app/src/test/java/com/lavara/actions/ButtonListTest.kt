package com.lavara.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonListTest {

    private fun c(text: String? = null, desc: String? = null, inner: String? = null, top: Int = 0, left: Int = 0) =
        ButtonList.Candidate(text, desc, inner, top, left)

    @Test
    fun nombre_usaTextoDescripcionOTextoDeAdentro() {
        assertEquals("Reproducir", ButtonList.label(c(desc = "Reproducir")))
        assertEquals("Enviar", ButtonList.label(c(text = "  Enviar ", desc = "otro")))
        assertEquals("Biblioteca", ButtonList.label(c(inner = "Biblioteca")))
        assertNull(ButtonList.label(c()))
    }

    @Test
    fun nombresInternos_noSeMuestranYGanaLoQueSeVe() {
        assertEquals("Iniciar viaje", ButtonList.label(c(desc = "btn_start_trip", inner = "Iniciar viaje")))
        assertEquals("Iniciar viaje", ButtonList.label(c(text = "startTripButton", inner = "Iniciar  viaje")))
        assertNull(ButtonList.label(c(desc = "com.app:id/start")))
        assertTrue(ButtonList.looksLikeCode("btn_start_trip"))
        assertTrue(ButtonList.looksLikeCode("startTrip"))
        assertFalse(ButtonList.looksLikeCode("Enviar"))
        assertFalse(ButtonList.looksLikeCode("OK"))
        assertFalse(ButtonList.looksLikeCode("Iniciar viaje"))
        assertFalse(ButtonList.looksLikeCode("Wi-Fi"))
    }

    @Test
    fun textosLargos_noSonBotonesYNoSeMuestran() {
        assertNull(ButtonList.label(c(text = "Hola, ¿nos vemos mañana a las ocho en la casa de siempre?")))
    }

    @Test
    fun textoEncimaDeUnBoton_tocaElBotonMasChicoDeAbajo() {
        val pantalla = ButtonList.Box(0, 0, 1000, 2000)
        val boton = ButtonList.Box(100, 1500, 900, 1650)
        assertEquals(1, ButtonList.smallestCovering(listOf(pantalla, boton), 500, 1575))
        assertEquals(0, ButtonList.smallestCovering(listOf(pantalla, boton), 500, 100))
        assertNull(ButtonList.smallestCovering(listOf(boton), 500, 100))
    }

    @Test
    fun lista_deArribaAbajoSinRepetir() {
        val list = ButtonList.from(
            listOf(
                c(desc = "Siguiente", top = 900, left = 600),
                c(desc = "Reproducir", top = 900, left = 400),
                c(text = "Buscar", top = 100),
                c(desc = "reproducir", top = 1200),
            ),
        )
        assertEquals(listOf("Buscar", "Reproducir", "Siguiente"), list)
        // Lo elegido se vuelve a encontrar al tocar.
        assertEquals(3, ButtonMatch.score(list[1], null, "Reproducir", null))
    }
}
