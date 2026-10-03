package com.lavara.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavara.LaVaraApp
import com.lavara.automation.Automation
import com.lavara.triggers.Trigger
import com.lavara.ui.MainActivity
import com.lavara.ui.actionTitle
import com.lavara.ui.theme.LaVaraTheme
import kotlinx.coroutines.launch

/** Al poner el widget en la pantalla de inicio: elegir qué automatización ejecuta. Se ofrecen las "a mano". */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Si el usuario sale sin elegir, Android quita el widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val container = (application as LaVaraApp).container
        setContent {
            LaVaraTheme {
                val all by remember { container.automationRepository.observeAll() }.collectAsState(initial = null as List<Automation>?)
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("¿Qué hace este botón?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Elegí una automatización \"a mano\". Al tocar el widget se ejecuta.", style = MaterialTheme.typography.bodyMedium)
                        val manual = all.orEmpty().filter { it.trigger is Trigger.Manual }
                        if (all != null && manual.isEmpty()) {
                            Text(
                                "No tenés automatizaciones \"a mano\". Creá una en La Vara con el disparador \"A mano\" y volvé a poner el widget.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java)); finish() }) {
                                Text("Abrir La Vara")
                            }
                        }
                        manual.forEach { automation -> Option(automation) { choose(widgetId, automation) } }
                    }
                }
            }
        }
    }

    @Composable
    private fun Option(automation: Automation, onClick: () -> Unit) {
        Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Column(Modifier.padding(14.dp)) {
                Text(automation.name, fontWeight = FontWeight.Bold)
                Text(
                    automation.actions.joinToString(", ") { actionTitle(it).lowercase() }.ifBlank { "sin acciones" } +
                        if (automation.enabled) "" else " · desactivada",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    private fun choose(widgetId: Int, automation: Automation) {
        val container = (application as LaVaraApp).container
        WidgetLinks(this).set(widgetId, automation.id)
        container.logger.info("Widget", "Widget nuevo para ${automation.name}", automation.id)
        container.appScope.launch {
            AutomationWidget.render(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId)
            runOnUiThread {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }
    }

    companion object {
        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
