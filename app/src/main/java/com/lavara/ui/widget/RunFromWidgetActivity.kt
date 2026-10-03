package com.lavara.ui.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.lavara.LaVaraApp
import com.lavara.automation.ExecutionStatus
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pantalla invisible que abre el widget. Ejecuta la automatización y se cierra. Como está a la vista,
 * Android deja abrir otras apps sin "Mostrar sobre otras apps".
 */
class RunFromWidgetActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null) {
            finish()
            return
        }
        val container = (application as LaVaraApp).container
        container.appScope.launch {
            container.logger.info("Widget", "Se tocó el widget de $id", id)
            val result = container.automationRunner
                .handle(TriggerEvent.ManualRun(id, "widget-" + UUID.randomUUID()))
                .firstOrNull { it.automationId == id }
            val name = container.automationRepository.find(id)?.name ?: id
            val message = when (result?.status) {
                ExecutionStatus.EXECUTED -> "$name: listo"
                null -> "$name: no se ejecutó"
                else -> "$name: ${result.reason}"
            }
            // Si salió bien no aparece nada, salvo que el usuario quiera ver sus notificaciones;
            // si falló, solo con "Avisar cuando algo falla" encendido.
            val prefs = container.actionExecutor.notificationPrefs
            val quiet = if (result?.status == ExecutionStatus.EXECUTED) !prefs.showOwn else !prefs.notifyErrors
            runOnUiThread {
                if (!quiet) Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "com.lavara.extra.AUTOMATION_ID"

        fun intent(context: Context, automationId: String): Intent =
            Intent(context, RunFromWidgetActivity::class.java)
                .putExtra(EXTRA_ID, automationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
}
