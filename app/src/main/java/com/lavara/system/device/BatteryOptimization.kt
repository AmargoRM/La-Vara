package com.lavara.system.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * El ahorro de batería de Android (y de Motorola) puede retrasar o cerrar La Vara en segundo plano.
 * Pedir la exclusión está permitido porque la app no se publica en Google Play.
 */
class BatteryOptimization(private val context: Context) {

    fun isExcluded(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    // Play restringe este pedido; La Vara se instala por APK (ver docs/LIMITES_ANDROID.md).
    @SuppressLint("BatteryLife")
    fun requestExclusion() {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
