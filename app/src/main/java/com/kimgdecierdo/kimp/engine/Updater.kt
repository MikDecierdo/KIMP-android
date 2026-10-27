package com.kimgdecierdo.kimp.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.kimgdecierdo.kimp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates KIMP itself from the android-v* releases on MikDecierdo/KIMP-download.
 * The new APK is downloaded into the cache and handed to Android's installer; Android only
 * accepts it if it's signed with the same key, so a tampered file can't replace the app.
 */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/MikDecierdo/KIMP-download/releases?per_page=30"

    data class Release(val version: String, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** "1.0.10" > "1.0.9": compares the numbers, not the text. */
    fun newer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "KIMP-Android/${BuildConfig.VERSION_NAME}")
    }

    /** Newest Android release with an APK for this phone's processor, or null. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val releases = open(RELEASES).run {
            setRequestProperty("Accept", "application/vnd.github+json")
            try { JSONArray(inputStream.bufferedReader().use { it.readText() }) } finally { disconnect() }
        }
        (0 until releases.length()).map { releases.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") && it.optString("tag_name").startsWith("android-v") }
            .mapNotNull { release ->
                val assets = release.getJSONArray("assets").let { list -> (0 until list.length()).map { list.getJSONObject(it) } }
                val apk = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
                    assets.firstOrNull { it.optString("name").endsWith("-$abi.apk") }
                } ?: return@mapNotNull null
                Release(release.optString("tag_name").removePrefix("android-v"), apk.optString("browser_download_url"), apk.optLong("size"))
            }
            .reduceOrNull { best, next -> if (newer(next.version, best.version)) next else best }
    }

    /** Looks for an update; quiet = don't show errors (used for the check on start). */
    suspend fun check(quiet: Boolean = false) {
        if (_state.value is State.Downloading) return
        _state.value = State.Checking
        _state.value = try {
            val release = latest()
            if (release != null && newer(release.version, BuildConfig.VERSION_NAME)) State.Available(release) else State.UpToDate
        } catch (e: Exception) {
            Log.w(TAG, "update check failed", e)
            if (quiet) State.Idle else State.Failed("Couldn't check for updates")
        }
    }

    /** Downloads the APK, then opens Android's install screen. */
    suspend fun install(context: Context, release: Release) {
        _state.value = State.Downloading(release, 0f)
        try {
            val apk = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
                val file = File(dir, "KIMP-${release.version}.apk")
                val connection = open(release.apkUrl)
                try {
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                done += n
                                if (total > 0) _state.value = State.Downloading(release, done.toFloat() / total)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                file
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _state.value = State.Available(release)
        } catch (e: Exception) {
            Log.w(TAG, "update download failed", e)
            _state.value = State.Failed("Update download failed")
        }
    }
}
