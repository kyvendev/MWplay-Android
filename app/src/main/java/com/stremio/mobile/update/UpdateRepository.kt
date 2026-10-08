package com.stremio.mobile.update

import android.content.Context
import android.os.Build
import com.stremio.mobile.BuildConfig
import com.stremio.mobile.core.extensions.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val apkName: String,
    val apkUrl: String,
    val sha256Url: String,
    val releaseNotes: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val bytesRead: Long, val totalBytes: Long?, val progress: Float?) : UpdateState
    data class ReadyToInstall(val file: File, val needsUnknownSourcesPermission: Boolean = false) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateRepository(private val context: Context) {
    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MWPlay/${BuildConfig.VERSION_NAME}")
        }
        connection.use {
            if (it.responseCode !in 200..299) return@withContext UpdateState.UpToDate
            val response = JSONArray(it.inputStream.bufferedReader().use { reader -> reader.readText() })
            val releases = buildList {
                for (index in 0 until response.length()) {
                    val release = response.optJSONObject(index) ?: continue
                    val assets = release.optJSONArray("assets") ?: continue
                    val parsedAssets = buildList {
                        for (assetIndex in 0 until assets.length()) {
                            val asset = assets.optJSONObject(assetIndex) ?: continue
                            val name = asset.optString("name")
                            val url = asset.optString("browser_download_url")
                            if (name.isNotBlank() && url.isNotBlank()) add(ReleaseAsset(name, url))
                        }
                    }
                    add(ReleaseCandidate(
                        tagName = release.optString("tag_name"),
                        assets = parsedAssets,
                        releaseNotes = release.optString("body").takeIf { body -> body.isNotBlank() }.orEmpty(),
                        draft = release.optBoolean("draft"),
                        prerelease = release.optBoolean("prerelease"),
                    ))
                }
            }
            val selected = selectUpdateRelease(releases, RELEASE_CHANNEL, BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList())
                ?: return@withContext UpdateState.UpToDate
            UpdateState.Available(UpdateInfo(selected.versionName, selected.tagName, selected.apk.name, selected.apk.url, selected.checksum.url, selected.releaseNotes))
        }
    }

    suspend fun download(info: UpdateInfo, onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }
        val destination = File(updatesDir, info.apkName)
        val partial = File(updatesDir, "${info.apkName}.part")
        if (partial.exists()) partial.delete()
        val expected = fetchExpectedSha256(info.sha256Url, info.apkName)
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.android.package-archive")
            setRequestProperty("User-Agent", "MWPlay/${BuildConfig.VERSION_NAME}")
        }
        connection.use {
            if (it.responseCode !in 200..299) throw IllegalStateException("APK download failed with HTTP ${it.responseCode}.")
            val total = it.contentLengthLong.takeIf { length -> length > 0L }
            var bytesRead = 0L
            it.inputStream.use { input -> partial.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    bytesRead += read
                    onProgress(bytesRead, total)
                }
            } }
        }
        if (!partial.sha256().equals(expected, ignoreCase = true)) {
            partial.delete()
            throw IllegalStateException("Downloaded APK checksum did not match the release checksum.")
        }
        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) { partial.copyTo(destination, overwrite = true); partial.delete() }
        destination
    }

    private fun fetchExpectedSha256(checksumUrl: String, apkName: String): String {
        val connection = (URL(checksumUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Accept", "text/plain"); setRequestProperty("User-Agent", "MWPlay/${BuildConfig.VERSION_NAME}")
        }
        return connection.use {
            if (it.responseCode !in 200..299) throw IllegalStateException("Release checksum download failed with HTTP ${it.responseCode}.")
            val checksumText = it.inputStream.bufferedReader().use { reader -> reader.readText() }
            expectedReleaseChecksum(checksumText, apkName)
                ?: throw IllegalStateException("Release checksum is missing or invalid for the selected APK.")
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val read = input.read(buffer); if (read <= 0) break; digest.update(buffer, 0, read) }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) }
    }

    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/kyvendev/MWplay-Android/releases?per_page=30"
        private val RELEASE_CHANNEL = ReleaseChannel.MOBILE
    }
}

