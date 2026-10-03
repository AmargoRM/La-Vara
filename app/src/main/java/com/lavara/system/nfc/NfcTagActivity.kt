package com.lavara.system.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle
import com.lavara.LaVaraApp
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pantalla invisible que Android abre al acercar una etiqueta grabada por La Vara. Ejecuta las
 * automatizaciones de esa etiqueta y se cierra. Como está a la vista, se pueden abrir apps sin permisos extra.
 */
class NfcTagActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // El código es secreto y al azar: otra app no puede adivinarlo para disparar automatizaciones.
        val code = intent?.takeIf { it.action == NfcAdapter.ACTION_NDEF_DISCOVERED }?.let { NfcTags.codeOf(it.data) }
        if (code == null) {
            finish()
            return
        }
        val container = (application as LaVaraApp).container
        container.appScope.launch {
            try {
                val results = container.automationRunner.handle(TriggerEvent.NfcTagScanned(code, UUID.randomUUID().toString()))
                if (results.isEmpty()) container.logger.warn("NFC", "Ninguna automatización activa usa la etiqueta $code")
            } finally {
                runOnUiThread { finish() }
            }
        }
    }
}
