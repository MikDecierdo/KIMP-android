package com.kimgdecierdo.kimp.engine

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val TAG = "KIMP"

/**
 * The download engine: yt-dlp + Python + ffmpeg from youtubedl-android, plus QuickJS-ng
 * (app/src/main/jniLibs/<abi>/libqjs.so), the JavaScript runtime yt-dlp needs for YouTube.
 */
object Engine {
    sealed interface Status {
        data object Starting : Status
        data object Updating : Status
        data class Ready(val ytdlpVersion: String?) : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Starting)
    val status: StateFlow<Status> = _status
    private val ready = CompletableDeferred<Unit>()

    private const val UPDATE_EVERY_MS = 24L * 60 * 60 * 1000

    fun start(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
            } catch (e: Exception) {
                Log.e(TAG, "engine init failed", e)
                _status.value = Status.Failed(e.message ?: "The download engine failed to start")
                ready.completeExceptionally(e)
                return@launch
            }
            update(context, onlyIfStale = true)
            ready.complete(Unit)
        }
    }

    suspend fun awaitReady() = ready.await()

    /** Brings yt-dlp up to date (YouTube changes often). Returns a short message for the UI. */
    suspend fun update(context: Context, onlyIfStale: Boolean = false): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("engine", Context.MODE_PRIVATE)
        val last = prefs.getLong("updated", 0L)
        if (onlyIfStale && System.currentTimeMillis() - last < UPDATE_EVERY_MS) {
            _status.value = Status.Ready(version(context))
            return@withContext "Already up to date"
        }
        _status.value = Status.Updating
        val message = try {
            val result = YoutubeDL.getInstance().updateYoutubeDL(context)
            prefs.edit().putLong("updated", System.currentTimeMillis()).apply()
            "yt-dlp: ${result ?: "done"}"
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update failed", e)
            "Update failed: ${e.message}"
        }
        _status.value = Status.Ready(version(context))
        message
    }

    fun version(context: Context): String? = try {
        YoutubeDL.getInstance().version(context)
    } catch (e: Exception) {
        null
    }

    fun quickJs(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")

    /** A yt-dlp request with the options every KIMP download shares. */
    fun request(context: Context, url: String) = YoutubeDLRequest(url).apply {
        val qjs = quickJs(context)
        if (qjs.exists()) addOption("--js-runtimes", "quickjs:${qjs.absolutePath}")
        // fetch YouTube's challenge-solver script if this yt-dlp build doesn't include it
        addOption("--remote-components", "ejs:github")
        addOption("--no-mtime")
        addOption("--trim-filenames", "150")
    }
}

/** H.264 + AAC first so the MP4 plays everywhere; plain HTTPS formats first (same as the Windows app). */
fun videoFormat(height: Int?, exact: Boolean): String {
    val h = when {
        height == null -> ""
        exact -> "[height=$height]"
        else -> "[height<=$height]"
    }
    val fallback = if (height == null) "best" else "best[height<=$height]"
    return "bestvideo$h[vcodec^=avc1][protocol^=http]+bestaudio[ext=m4a][protocol^=http]/" +
        "bestvideo$h[protocol^=http]+bestaudio[protocol^=http]/" +
        "bestvideo$h+bestaudio/$fallback"
}
