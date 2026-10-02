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
            container.refreshTriggers("app iniciada")
        }
    }

    /**
     * Los ejemplos se crean una sola vez, desactivados: si el usuario los borra, no vuelven.
     * Cada grupo tiene su clave para que los ejemplos nuevos aparezcan también a quien ya tenía La Vara.
     */
    private suspend fun createTemplatesOnce() {
        val settings = container.settingsRepository
        for ((key, build) in Templates.groups()) {
            if (settings.get(key) != null) continue
            val now = container.clock.now().toInstant().toEpochMilli()
            for (automation in build(now)) {
                if (container.automationRepository.find(automation.id) != null) continue
                container.automationRepository.save(automation)
                container.logger.info("App", "Se creó la automatización de ejemplo \"${automation.name}\" (desactivada)", automation.id)
            }
            settings.set(key, "1")
        }
    }
}
