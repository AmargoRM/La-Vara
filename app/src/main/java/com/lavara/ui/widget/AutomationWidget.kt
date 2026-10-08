package com.lavara.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.lavara.LaVaraApp
import com.lavara.R
import com.lavara.actions.Action
import com.lavara.actions.NavigationApp
import com.lavara.automation.Automation
import com.lavara.system.device.InstalledApps
import kotlinx.coroutines.launch

/**
 * Widget de 1×1: un botón que ejecuta una automatización. Muestra el ícono de la app que abre (WhatsApp,
 * Waze…) o un emoji según su primera acción, y el nombre abajo. Qué automatización ejecuta cada widget se
 * guarda en [WidgetLinks].
 */
class AutomationWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val container = (context.applicationContext as LaVaraApp).container
        container.appScope.launch {
            try {
                for (id in appWidgetIds) render(context, manager, id)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetLinks(context).remove(it) }
    }

    companion object {
        /** Vuelve a dibujar todos los widgets (por ejemplo, después de cambiar el nombre de una automatización). */
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, AutomationWidget::class.java))
            for (id in ids) render(context, manager, id)
        }

        suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val container = (context.applicationContext as LaVaraApp).container
            val automationId = WidgetLinks(context).get(widgetId)
            val automation = automationId?.let { container.automationRepository.find(it) }
            val views = RemoteViews(context.packageName, R.layout.widget_automation)
            views.setTextViewText(
                R.id.widget_name,
                automation?.name ?: if (automationId == null) "Elegir automatización" else "Borrada",
            )
            val app = automation?.let { appOf(it) }
            val icon = app?.let { InstalledApps(context).icon(it) }
            if (icon != null) {
                views.setImageViewBitmap(R.id.widget_icon, icon.toBitmap(96, 96))
                views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
                views.setViewVisibility(R.id.widget_emoji, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_emoji, automation?.let { emojiOf(it) } ?: "⚡")
                views.setViewVisibility(R.id.widget_icon, View.GONE)
                views.setViewVisibility(R.id.widget_emoji, View.VISIBLE)
            }
            val tap = if (automation != null) {
                RunFromWidgetActivity.intent(context, automation.id)
            } else {
                // Sin automatización elegida (o borrada): tocar abre la elección.
                WidgetConfigActivity.intent(context, widgetId)
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, widgetId, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            manager.updateAppWidget(widgetId, views)
        }

        /** La app que abre o en la que toca la automatización, para usar su ícono. */
        fun appOf(automation: Automation): String? = automation.actions.firstNotNullOfOrNull { action ->
            when (action) {
                is Action.OpenApp -> action.packageName
                is Action.TapInApp -> action.packageName.ifBlank { null }
                is Action.TouchScreen -> action.packageName.ifBlank { null }
                is Action.WhatsAppMessage -> "com.whatsapp"
                is Action.Navigate -> if (action.app == NavigationApp.WAZE) "com.waze" else "com.google.android.apps.maps"
                else -> null
            }
        }

        /** Emoji según la primera acción que no abre una app. */
        fun emojiOf(automation: Automation): String = when (automation.actions.firstOrNull { it !is Action.Delay }) {
            is Action.ShowNotification -> "🔔"
            is Action.Flashlight -> "🔦"
            is Action.SetVolume -> "🔊"
            is Action.SetRingerMode -> "📳"
            is Action.DoNotDisturb -> "🌙"
            is Action.SetBrightness -> "☀️"
            is Action.OpenSystemPanel -> "📶"
            is Action.OpenUrl -> "🔗"
            is Action.SendSms -> "✉️"
            is Action.DialNumber -> "📞"
            is Action.RunAutomation -> "▶️"
            is Action.Vibrate -> "📳"
            is Action.CopyToClipboard -> "📋"
            is Action.ShareText -> "📤"
            is Action.ReplyToNotification -> "💬"
            is Action.TapNotificationButton -> "🔘"
            is Action.MediaControl -> "🎵"
            is Action.IfElse -> "🔀"
            else -> "⚡"
        }
    }
}

/** Qué automatización ejecuta cada widget (por su número de widget). */
class WidgetLinks(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun get(widgetId: Int): String? = prefs.getString("w$widgetId", null)

    fun set(widgetId: Int, automationId: String) = prefs.edit().putString("w$widgetId", automationId).apply()

    fun remove(widgetId: Int) = prefs.edit().remove("w$widgetId").apply()
}
