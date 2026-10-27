package com.kimgdecierdo.kimp.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class Kind { MUSIC, VIDEO }

/** One download request. `height` and `label` are only used for video. */
data class Job(val kind: Kind, val url: String, val playlist: Boolean, val height: Int? = null, val label: String = "")

/** What a tab's progress panel shows. */
data class PanelState(
    val title: String = "Ready",
    val progress: Float = 0f,
    val status: String = "Nothing downloading yet",
    val log: List<String> = emptyList(),
)

/** Shared download state: one panel per tab, and only one job runs at a time. */
object Downloads {
    private val _busy = MutableStateFlow<Kind?>(null)
    val busy: StateFlow<Kind?> = _busy
    private val panels = Kind.entries.associateWith { MutableStateFlow(PanelState()) }

    fun panel(kind: Kind): StateFlow<PanelState> = panels.getValue(kind)

    fun tryBegin(job: Job): Boolean {
        if (!_busy.compareAndSet(null, job.kind)) return false
        panels.getValue(job.kind).value = PanelState(
            title = "Starting...",
            status = if (job.kind == Kind.VIDEO) "Video · ${job.label}" else if (job.playlist) "Playlist" else "Song",
        )
        Log.i(TAG, "begin ${job.kind} ${job.url} ${job.label}")
        return true
    }

    fun progress(kind: Kind, fraction: Float, status: String, title: String? = null) {
        panels.getValue(kind).update { it.copy(progress = fraction, status = status, title = title ?: it.title) }
    }

    fun log(kind: Kind, line: String) {
        Log.i(TAG, "log $line")
        panels.getValue(kind).update { it.copy(log = (it.log + line).takeLast(200)) }
    }

    /** result is "ok", "stopped" or "failed" (logged for the self-test as "done <result>"). */
    fun finish(kind: Kind, result: String, message: String, saved: Int, skipped: Int) {
        Log.i(TAG, "done $result saved=$saved skipped=$skipped")
        panels.getValue(kind).update {
            it.copy(
                title = message,
                progress = if (result == "ok" && saved > 0) 1f else it.progress,
                status = "$saved saved" + if (skipped > 0) ", $skipped skipped" else "",
            )
        }
        _busy.value = null
    }
}
