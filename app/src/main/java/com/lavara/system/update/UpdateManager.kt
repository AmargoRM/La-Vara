package com.lavara.system.update

import android.content.Context
import com.lavara.BuildConfig
import java.io.File
import java.time.LocalDate

/** Une las piezas: token, consulta a GitHub, estado guardado, notificación y descarga. */
class UpdateManager(
    private val context: Context,
    val tokenStore: TokenStore,
    private val statusStore: UpdateStatusStore,
    private val client: GitHubReleaseClient,
    private val notifier: UpdateNotifier,
) {
    private val installedVersionCode = BuildConfig.VERSION_CODE

    fun status(): UpdateStatus = statusStore.load()

    /** Revisa GitHub, guarda el resultado y, si [notify], avisa una sola vez por versión. */
    suspend fun check(notify: Boolean): UpdateStatus {
        val now = System.currentTimeMillis()
        val token = tokenStore.load()
        val previous = statusStore.load()
        val status = if (token == null) {
            UpdateStatus(now, UpdateErrors.NO_TOKEN, null, previous.tokenExpiry)
        } else {
            when (val result = client.fetchLatest(token)) {
                is FetchResult.Error -> previous.copy(lastCheckMillis = now, lastMessage = result.message)
                is FetchResult.Ok -> {
                    val newer = result.release.takeIf { ReleaseParser.isNewer(it, installedVersionCode) }
                    val message = if (newer != null) {
                        "Hay una versión nueva: ${newer.versionName}."
                    } else {
                        "Tenés la última versión (${BuildConfig.VERSION_NAME})."
                    }
                    UpdateStatus(now, message, newer, result.tokenExpiry ?: previous.tokenExpiry)
                }
            }
        }
        statusStore.save(status)

        if (notify) {
            status.available?.let {
                if (statusStore.lastNotifiedVersionCode != it.versionCode) {
                    notifier.notifyNewVersion(it)
                    statusStore.lastNotifiedVersionCode = it.versionCode
                }
            }
            status.tokenExpiry?.let { expiry ->
                val today = LocalDate.now()
                if (TokenExpiry.shouldWarn(expiry, today) && statusStore.lastExpiryWarning != today.toString()) {
                    notifier.notifyTokenExpiring(TokenExpiry.daysLeft(expiry, today))
                    statusStore.lastExpiryWarning = today.toString()
                }
            }
        }
        return status
    }

    fun apkFile(): File = File(context.cacheDir, "actualizacion/La-Vara.apk")

    /** Devuelve null si salió bien, o el mensaje de error. */
    suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): String? {
        val token = tokenStore.load() ?: return UpdateErrors.NO_TOKEN
        return client.downloadApk(token, release, apkFile(), onProgress)
    }
}
