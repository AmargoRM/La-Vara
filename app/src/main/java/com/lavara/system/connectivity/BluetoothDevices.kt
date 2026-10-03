package com.lavara.system.connectivity

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Aparatos Bluetooth vinculados al teléfono, para elegir uno en el editor. */
object BluetoothDevices {

    /** Permiso que Android 12+ pide para ver los aparatos y recibir sus conexiones. */
    val PERMISSION: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

    fun hasPermission(context: Context): Boolean =
        PERMISSION == null || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    data class Paired(val address: String, val name: String)

    /** Los aparatos vinculados, ordenados por nombre. Vacío si falta el permiso o el Bluetooth está apagado. */
    @SuppressLint("MissingPermission") // Se comprueba con hasPermission.
    fun paired(context: Context): List<Paired> {
        if (!hasPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { Paired(it.address, nameOf(it) ?: it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun nameOf(device: BluetoothDevice): String? = try {
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null) ?: device.name
    } catch (e: SecurityException) {
        null
    }
}
