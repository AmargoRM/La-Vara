package com.lavara.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonListTest {

    private fun c(text: String? = null, desc: String? = null, inner: String? = null, id: String? = null, top: Int = 0, left: Int = 0) =
        ButtonList.Candidate(text, desc, inner, id, top, left)

    @Test
    fun nombre_usaTextoDescripcionTextoDeAdentroONombreInterno() {
        assertEquals("Reproducir", ButtonList.label(c(desc = "Reproducir")))
        assertEquals("Enviar", ButtonList.label(c(text = "  Enviar ", desc = "otro")))
        assertEquals("Biblioteca", ButtonList.label(c(inner = "Biblioteca")))
        assertEquals("send", ButtonList.label(c(id = "com.whatsapp:id/send")))
        assertNull(ButtonList.label(c()))
    }

    @Test
    fun textosLargos_noSonBotonesYNoSeMuestran() {
        assertNull(ButtonList.label(c(text = "Hola, ¿nos vemos mañana a las ocho en la casa de siempre?")))
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
