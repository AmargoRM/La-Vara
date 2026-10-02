package com.lavara.core

import android.content.Context
import com.lavara.automation.AutomationEngine
import com.lavara.data.AutomationRepository
import com.lavara.data.LaVaraDatabase
import com.lavara.data.RunRepository
import com.lavara.data.SettingsRepository
import com.lavara.logging.AppLogger
import com.lavara.logging.RoomLogger
import com.lavara.system.AutomationRunner
import com.lavara.system.alarm.AlarmScheduler
import com.lavara.system.device.AndroidActionExecutor
import com.lavara.system.device.AndroidDeviceState
import com.lavara.system.device.BatteryOptimization
import com.lavara.system.update.ApkInstaller
import com.lavara.system.update.GitHubReleaseClient
import com.lavara.system.update.TokenStore
import com.lavara.system.update.UpdateManager
import com.lavara.system.update.UpdateNotifier
import com.lavara.system.update.UpdateStatusStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Inyección de dependencias manual: aquí se crean y conectan las piezas de la app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Trabajo de fondo que vive mientras viva la app (por ejemplo, escribir logs). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clock: Clock = DeviceClock

    val database: LaVaraDatabase by lazy { LaVaraDatabase.create(appContext) }
    val logger: AppLogger by lazy { RoomLogger(database.logDao(), appScope, clock) }
    val automationRepository by lazy { AutomationRepository(database.automationDao(), logger) }
    val runRepository by lazy { RunRepository(database) }
    val settingsRepository by lazy { SettingsRepository(database.settingDao()) }

    val deviceState = AndroidDeviceState(appContext)
    val actionExecutor = AndroidActionExecutor(appContext)
    val batteryOptimization = BatteryOptimization(appContext)
    val engine by lazy { AutomationEngine(automationRepository, actionExecutor, clock, deviceState) }
    val automationRunner by lazy { AutomationRunner(engine, automationRepository, runRepository, logger) }
    val alarmScheduler by lazy { AlarmScheduler(appContext, automationRepository, settingsRepository, logger, clock) }

    val updateNotifier = UpdateNotifier(appContext)
    val apkInstaller = ApkInstaller(appContext)
    val updateManager = UpdateManager(
        context = appContext,
        tokenStore = TokenStore(appContext),
        statusStore = UpdateStatusStore(appContext),
        client = GitHubReleaseClient(),
        notifier = updateNotifier,
        logger = logger,
    )
}
