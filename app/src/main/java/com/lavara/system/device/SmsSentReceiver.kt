package com.lavara.system.device

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.lavara.LaVaraApp

/** Android avisa si el SMS salió o no (sin señal, modo avión, sin saldo…). Se escribe en los registros. */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val to = intent.getStringExtra(EXTRA_TO) ?: "?"
        val part = intent.getIntExtra(EXTRA_PART, 1)
        val total = intent.getIntExtra(EXTRA_TOTAL, 1)
        val which = if (total > 1) " (parte $part de $total)" else ""
        val logger = (context.applicationContext as LaVaraApp).container.logger
        when (val code = resultCode) {
            Activity.RESULT_OK -> logger.info("SMS", "SMS a $to enviado$which.")
            else -> logger.error("SMS", "El SMS a $to no salió$which: ${reason(code)}")
        }
    }

    private fun reason(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no hay señal."
        SmsManager.RESULT_ERROR_RADIO_OFF -> "el teléfono está en modo avión o sin red móvil."
        SmsManager.RESULT_ERROR_NULL_PDU -> "el operador no aceptó el mensaje."
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "falla del operador (revisá el saldo o la SIM elegida para SMS)."
        else -> "Android devolvió el código $code."
    }

    companion object {
        const val EXTRA_TO = "to"
        const val EXTRA_PART = "part"
        const val EXTRA_TOTAL = "total"
    }
}
