package com.lavara.core

import android.content.Context
import com.lavara.system.update.ApkInstaller
import com.lavara.system.update.GitHubReleaseClient
import com.lavara.system.update.TokenStore
import com.lavara.system.update.UpdateManager
import com.lavara.system.update.UpdateNotifier
import com.lavara.system.update.UpdateStatusStore

/** Inyección de dependencias manual: aquí se crean y conectan las piezas de la app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val updateNotifier = UpdateNotifier(appContext)
    val apkInstaller = ApkInstaller(appContext)
    val updateManager = UpdateManager(
        context = appContext,
        tokenStore = TokenStore(appContext),
        statusStore = UpdateStatusStore(appContext),
        client = GitHubReleaseClient(),
        notifier = updateNotifier,
    )
}
