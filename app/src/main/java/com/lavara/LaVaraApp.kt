package com.lavara

import android.app.Application
import com.lavara.automation.Templates
import com.lavara.core.AppContainer
import com.lavara.system.update.UpdateWorker
import kotlinx.coroutines.launch

class LaVaraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.logger.info("App", "App iniciada: versión ${BuildConfig.VERSION_NAME} (compilación ${BuildConfig.VERSION_CODE})")
        UpdateWorker.schedule(this)
        container.appScope.launch {
            createTemplatesOnce()
            container.alarmScheduler.reschedule("app iniciada")
        }
    }

    /** "Prueba Vara" se crea una sola vez: si el usuario la borra, no vuelve a aparecer. */
    private suspend fun createTemplatesOnce() {
        val settings = container.settingsRepository
        if (settings.get(KEY_TEMPLATES) != null) return
        if (container.automationRepository.find(Templates.PRUEBA_VARA_ID) == null) {
            val now = container.clock.now().toInstant().toEpochMilli()
            container.automationRepository.save(Templates.pruebaVara(now))
            container.logger.info("App", "Se creó la automatización de ejemplo \"Prueba Vara\" (desactivada)", Templates.PRUEBA_VARA_ID)
        }
        settings.set(KEY_TEMPLATES, "1")
    }

    private companion object {
        const val KEY_TEMPLATES = "plantillas_creadas"
    }
}
