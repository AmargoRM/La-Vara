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
import com.lavara.system.device.DeviceWatcher
import com.lavara.system.device.UnlockQueue
import com.lavara.system.device.UnlockWaitService
import com.lavara.ui.widget.AutomationWidget
import com.lavara.system.location.GeofenceSync
import com.lavara.system.location.LocationAccess
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
    val automationRunner by lazy {
        AutomationRunner(engine, automationRepository, runRepository, logger) { ids -> unlockQueue.wait(ids) }
    }
    val unlockQueue by lazy {
        UnlockQueue(appContext, settingsRepository, automationRepository, logger, clock, appScope, { automationRunner }) {
            actionExecutor.canOpenAppsInBackground()
        }
    }
    val alarmScheduler by lazy { AlarmScheduler(appContext, automationRepository, settingsRepository, logger, clock) }
    val locationAccess = LocationAccess(appContext)
    val geofenceSync by lazy { GeofenceSync(appContext, automationRepository, locationAccess, logger) }
    val deviceWatcher by lazy { DeviceWatcher(appContext, automationRepository, settingsRepository, logger) { automationRunner } }

    /**
     * Después de cualquier cambio (automatización guardada, reinicio, cambio de hora): programa la
     * próxima alarma y enciende o apaga la vigilancia de batería y cargador. [reason] queda en el registro.
     */
    suspend fun refreshTriggers(reason: String) {
        alarmScheduler.reschedule(reason)
        deviceWatcher.sync(reason)
        geofenceSync.sync(reason)
        // Si quedó algo esperando el desbloqueo (por ejemplo, antes de reiniciar), se sigue esperando.
        if (unlockQueue.hasPending()) UnlockWaitService.start(appContext)
        // Los widgets muestran el nombre de su automatización: se redibujan por si cambió o se borró.
        AutomationWidget.refreshAll(appContext)
    }

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
