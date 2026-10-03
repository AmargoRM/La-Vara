package com.lavara.system.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.provider.Settings
import java.util.UUID

/**
 * Etiquetas NFC. La Vara graba en la etiqueta una dirección "lavara://nfc/CODIGO" y la marca de la app;
 * así Android abre La Vara al acercar el teléfono, aunque esté cerrada. Android solo lee etiquetas con la
 * pantalla encendida y desbloqueada.
 */
object NfcTags {
    const val SCHEME = "lavara"
    const val HOST = "nfc"

    fun adapter(context: Context): NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    fun hasNfc(context: Context) = adapter(context) != null

    fun isOn(context: Context) = adapter(context)?.isEnabled == true

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun newCode(): String = UUID.randomUUID().toString().replace("-", "").take(10)

    /** El código grabado en una etiqueta, a partir de la dirección que leyó Android; null si no es de La Vara. */
    fun codeOf(uri: Uri?): String? =
        uri?.takeIf { it.scheme == SCHEME && it.host == HOST }?.lastPathSegment?.takeIf { it.isNotBlank() }

    /**
     * Mientras [activity] está a la vista, la próxima etiqueta que se acerque se graba con [code].
     * [onResult] recibe null si salió bien, o el motivo del error en palabras. Hay que llamar a [stop] al salir.
     */
    fun startWriting(activity: Activity, code: String, onResult: (String?) -> Unit) {
        val adapter = adapter(activity) ?: return onResult("Este teléfono no tiene NFC.")
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        adapter.enableReaderMode(activity, { tag -> onResult(write(tag, message(activity, code))) }, flags, null)
    }

    fun stop(activity: Activity) {
        runCatching { adapter(activity)?.disableReaderMode(activity) }
    }

    private fun message(context: Context, code: String) = NdefMessage(
        NdefRecord.createUri("$SCHEME://$HOST/$code"),
        // Asegura que la abra La Vara y no otra app.
        NdefRecord.createApplicationRecord(context.packageName),
    )

    private fun write(tag: Tag, message: NdefMessage): String? = try {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.use {
                it.connect()
                when {
                    !it.isWritable -> "La etiqueta está bloqueada contra escritura. Usá otra."
                    it.maxSize < message.byteArrayLength -> "La etiqueta es muy chica (${it.maxSize} bytes). Usá otra."
                    else -> {
                        it.writeNdefMessage(message)
                        null
                    }
                }
            }
        } else {
            val formatable = NdefFormatable.get(tag) ?: return "Esta etiqueta no se puede grabar (no es de tipo NDEF)."
            formatable.use {
                it.connect()
                it.format(message)
                null
            }
        }
    } catch (e: Exception) {
        "No se pudo grabar: ${e.message ?: "se alejó la etiqueta"}. Mantené el teléfono quieto sobre la etiqueta e intentá de nuevo."
    }
}
