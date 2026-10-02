package com.lavara.system.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Abre el instalador de Android con el APK descargado. Android siempre pide que el usuario
 * toque "Actualizar": una app normal no puede instalar en silencio.
 */
class ApkInstaller(private val context: Context) {

    /** Permiso "Instalar apps desconocidas" para La Vara (se concede desde Ajustes). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Comprueba que el archivo sea La Vara y más nueva; devuelve un mensaje de error o null. */
    fun validate(apk: File, installedVersionCode: Int): String? {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: return "El archivo descargado no es un APK válido. Probá descargarlo de nuevo."
        if (info.packageName != context.packageName) return "El archivo descargado no es La Vara."
        if (info.longVersionCode <= installedVersionCode) return "El archivo descargado no es más nuevo que la versión instalada."
        return null
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
