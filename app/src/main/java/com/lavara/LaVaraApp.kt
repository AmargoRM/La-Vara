package com.lavara

import android.app.Application
import com.lavara.core.AppContainer
import com.lavara.system.update.UpdateWorker

class LaVaraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UpdateWorker.schedule(this)
    }
}
