package com.lavara.system.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lavara.LaVaraApp
import java.util.concurrent.TimeUnit

/**
 * Revisión periódica de versiones nuevas. WorkManager la reprograma solo tras reiniciar
 * el teléfono y espera a que haya internet; no hay consultas continuas.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = (applicationContext as LaVaraApp).container.updateManager
        if (!manager.tokenStore.hasToken()) return Result.success()
        manager.check(notify = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "revisar_actualizaciones"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
