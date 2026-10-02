package com.lavara.system.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

sealed interface FetchResult {
    data class Ok(val release: ReleaseInfo, val tokenExpiry: LocalDate?) : FetchResult
    data class Error(val message: String) : FetchResult
}

/** Habla con la API de GitHub usando HttpURLConnection (API de Android, sin librerías). */
class GitHubReleaseClient(
    private val owner: String = "AmargoRM",
    private val repo: String = "La-Vara",
    private val tag: String = "ultima",
) {
    suspend fun fetchLatest(token: String): FetchResult = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$owner/$repo/releases/tags/$tag")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = conn.responseCode
            if (code != 200) return@withContext FetchResult.Error(UpdateErrors.forHttpCode(code))
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val release = ReleaseParser.parse(body)
                ?: return@withContext FetchResult.Error(UpdateErrors.BAD_RESPONSE)
            val expiry = TokenExpiry.parse(conn.getHeaderField("github-authentication-token-expiration"))
            FetchResult.Ok(release, expiry)
        } catch (e: IOException) {
            FetchResult.Error(UpdateErrors.NO_NETWORK)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Baja el APK a [dest]. GitHub responde con una redirección a un enlace temporal firmado;
     * se sigue a mano para no reenviar el token a otro servidor.
     */
    suspend fun downloadApk(
        token: String,
        release: ReleaseInfo,
        dest: File,
        onProgress: (Float) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val first = (URL(release.apkApiUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val conn = when (val code = first.responseCode) {
                200 -> first
                301, 302, 303, 307, 308 -> {
                    val location = first.getHeaderField("Location")
                    first.disconnect()
                    if (location == null) return@withContext UpdateErrors.BAD_RESPONSE
                    (URL(location).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }.also {
                        if (it.responseCode != 200) {
                            val msg = UpdateErrors.forHttpCode(it.responseCode)
                            it.disconnect()
                            return@withContext msg
                        }
                    }
                }
                else -> {
                    first.disconnect()
                    return@withContext UpdateErrors.forHttpCode(code)
                }
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".parcial")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            conn.disconnect()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            null
        } catch (e: IOException) {
            UpdateErrors.NO_NETWORK
        }
    }
}
