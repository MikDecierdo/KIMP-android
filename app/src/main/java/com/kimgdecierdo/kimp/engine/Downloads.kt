package com.kimgdecierdo.kimp.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class Kind { MUSIC, VIDEO }

/** One download request. `height` and `label` are only used for video. */
data class Job(val kind: Kind, val url: String, val playlist: Boolean, val height: Int? = null, val label: String = "")

/** A queued download; `name` is what the queue list shows. */
data class QueueItem(val id: Long, val job: Job, val name: String)

/** What a tab's progress panel shows. */
data class PanelState(
    val title: String = "Ready",
    val progress: Float = 0f,
    val status: String = "Nothing downloading yet",
    val log: List<String> = emptyList(),
)

/** Shared download state: a queue that runs one job at a time, and one progress panel per tab. */
object Downloads {
    private val _busy = MutableStateFlow<Kind?>(null)
    val busy: StateFlow<Kind?> = _busy
    private val _current = MutableStateFlow<QueueItem?>(null)
    val current: StateFlow<QueueItem?> = _current
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    /** Waiting jobs, in order (not the one running). */
    val queue: StateFlow<List<QueueItem>> = _queue
    private val ids = AtomicLong()
    private val panels = Kind.entries.associateWith { MutableStateFlow(PanelState()) }

    fun panel(kind: Kind): StateFlow<PanelState> = panels.getValue(kind)

    private fun same(a: Job, b: Job) = a.kind == b.kind && a.url == b.url && a.label == b.label

    /** Adds a job to the end of the queue; false if the same job is already running or waiting. */
    @Synchronized
    fun enqueue(job: Job, name: String): Boolean {
        if (_current.value?.let { same(it.job, job) } == true || _queue.value.any { same(it.job, job) }) return false
        _queue.value = _queue.value + QueueItem(ids.incrementAndGet(), job, name)
        Log.i(TAG, "queued ${job.kind} ${job.url} ${job.label}")
        return true
    }

    /** Takes the next waiting job and makes it the current one, or returns null when the queue is empty. */
    @Synchronized
    fun next(): QueueItem? {
        val item = _queue.value.firstOrNull() ?: return null
        _queue.value = _queue.value.drop(1)
        _current.value = item
        _busy.value = item.job.kind
        val job = item.job
        panels.getValue(job.kind).value = PanelState(
            title = "Starting...",
            status = if (job.kind == Kind.VIDEO) "Video · ${job.label}" else if (job.playlist) "Playlist" else "Song",
        )
        Log.i(TAG, "begin ${job.kind} ${job.url} ${job.label}")
        return item
    }

    @Synchronized
    fun remove(id: Long) {
        _queue.value = _queue.value.filterNot { it.id == id }
    }

    @Synchronized
    fun clear() {
        _queue.value = emptyList()
    }

    fun progress(kind: Kind, fraction: Float, status: String, title: String? = null) {
        panels.getValue(kind).update { it.copy(progress = fraction, status = status, title = title ?: it.title) }
    }

    fun log(kind: Kind, line: String) {
        Log.i(TAG, "log $line")
        panels.getValue(kind).update { it.copy(log = (it.log + line).takeLast(200)) }
    }

    /** result is "ok", "stopped" or "failed" (logged for the self-test as "done <result>"). */
    @Synchronized
    fun finish(kind: Kind, result: String, message: String, saved: Int, skipped: Int) {
        Log.i(TAG, "done $result saved=$saved skipped=$skipped")
        panels.getValue(kind).update {
            it.copy(
                title = message,
                progress = if (result == "ok" && saved > 0) 1f else it.progress,
                status = "$saved saved" + if (skipped > 0) ", $skipped skipped" else "",
            )
        }
        _current.value = null
        _busy.value = null
    }
}
