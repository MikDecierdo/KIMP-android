package com.kimgdecierdo.kimp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kimgdecierdo.kimp.Prefs
import com.kimgdecierdo.kimp.engine.Downloads
import com.kimgdecierdo.kimp.engine.Engine
import com.kimgdecierdo.kimp.engine.Job
import com.kimgdecierdo.kimp.engine.Kind
import com.kimgdecierdo.kimp.engine.ProbeResult
import com.kimgdecierdo.kimp.engine.defaultQuality
import com.kimgdecierdo.kimp.engine.probeQualities
import com.kimgdecierdo.kimp.service.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    sealed interface Probe {
        data object Idle : Probe
        data object Loading : Probe
        data class Failed(val message: String) : Probe
        data class Ready(val result: ProbeResult) : Probe
    }

    var musicUrl by mutableStateOf("")
    var musicPlaylist by mutableStateOf(false)
    var videoUrl by mutableStateOf("")
    var probe by mutableStateOf<Probe>(Probe.Idle)
        private set
    var quality by mutableStateOf<Int?>(null)
    var engineMessage by mutableStateOf<String?>(null)
        private set
    private var probeJob: kotlinx.coroutines.Job? = null

    fun startMusic() {
        val url = musicUrl.trim()
        if (url.isEmpty() || Downloads.busy.value != null) return
        DownloadService.start(getApplication(), Job(Kind.MUSIC, url, musicPlaylist))
    }

    fun findQualities() {
        val url = videoUrl.trim()
        if (url.isEmpty()) {
            probe = Probe.Failed("Paste a YouTube link first")
            return
        }
        probeJob?.cancel()
        probe = Probe.Loading
        quality = null
        probeJob = viewModelScope.launch {
            probe = try {
                val result = probeQualities(getApplication(), url)
                quality = defaultQuality(result.options)
                Probe.Ready(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Probe.Failed(friendly(e))
            }
        }
    }

    fun startVideo() {
        val ready = probe as? Probe.Ready ?: return
        val index = quality ?: return
        if (Downloads.busy.value != null) return
        if (videoUrl.trim() != ready.result.url) {
            probe = Probe.Failed("The link changed. Press Find qualities again")
            return
        }
        val option = ready.result.options[index]
        DownloadService.start(getApplication(), Job(Kind.VIDEO, ready.result.url, false, option.height, option.label))
    }

    fun stop() = DownloadService.stop(getApplication())

    fun updateEngine() {
        viewModelScope.launch {
            engineMessage = "Updating yt-dlp..."
            engineMessage = Engine.update(getApplication())
        }
    }

    fun setTheme(value: String) = Prefs.setTheme(getApplication(), value)

    /** A link shared from the YouTube app (or any app) fills both link fields. */
    fun sharedText(text: String) {
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        musicUrl = url
        videoUrl = url
    }

    private fun friendly(e: Exception): String =
        e.message?.lines()?.lastOrNull { it.isNotBlank() }?.removePrefix("ERROR: ")?.trim() ?: "Something went wrong"
}
