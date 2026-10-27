package com.kimgdecierdo.kimp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kimgdecierdo.kimp.R
import com.kimgdecierdo.kimp.engine.Downloads
import com.kimgdecierdo.kimp.engine.Engine
import com.kimgdecierdo.kimp.engine.Job
import com.kimgdecierdo.kimp.engine.Kind
import com.kimgdecierdo.kimp.engine.MediaSaver
import com.kimgdecierdo.kimp.engine.TAG
import com.kimgdecierdo.kimp.engine.videoFormat
import com.kimgdecierdo.kimp.ui.MainActivity
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Works through the download queue in the foreground, one job at a time, so it keeps going with the screen off. */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.kimgdecierdo.kimp.STOP"
        private const val ACTION_STOP_ALL = "com.kimgdecierdo.kimp.STOP_ALL"

        /** Queues a job and makes sure the service is working through the queue. */
        fun enqueue(context: Context, job: Job, name: String): Boolean {
            if (!Downloads.enqueue(job, name)) return false
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            return true
        }

        /** Stops the current download; the next one in the queue starts. */
        fun stop(context: Context) {
            context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_STOP))
        }

        /** Empties the queue and stops the current download. */
        fun stopAll(context: Context) {
            context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_STOP_ALL))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var processId: String? = null
    @Volatile private var stopRequested = false
    @Volatile private var lastStartId = 0
    private val working = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                Downloads.clear()
                stopCurrent()
            }
            ACTION_STOP -> stopCurrent()
        }
        if (intent?.action != null) {
            if (!working.get()) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        lastStartId = startId
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Starting...", 0, indeterminate = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (working.compareAndSet(false, true)) scope.launch { work() }
        return START_NOT_STICKY
    }

    private fun stopCurrent() {
        if (Downloads.current.value == null) return
        stopRequested = true
        processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
    }

    /** The only worker: runs queued jobs until the queue is empty, then lets the service stop. */
    private suspend fun work() {
        while (true) {
            while (true) {
                val item = Downloads.next() ?: break
                stopRequested = false
                run(item.job)
            }
            working.set(false)
            // A job queued between the last next() and here must not be left waiting
            if (Downloads.queue.value.isEmpty() || !working.compareAndSet(false, true)) break
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStartId)  // stays alive if another job came in meanwhile
    }

    private suspend fun run(job: Job) {
        val noun = if (job.kind == Kind.VIDEO) "Video" else "Song"
        val work = File(cacheDir, "jobs/" + UUID.randomUUID()).apply { mkdirs() }
        var saved = 0
        var skipped = 0
        var result = "ok"
        var message = "All done!"
        try {
            Engine.awaitReady()
            val request = Engine.request(this, job.url).apply {
                if (job.kind == Kind.MUSIC) {
                    addOption("-f", "bestaudio[protocol^=http]/bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "320K")
                } else {
                    addOption("-f", videoFormat(job.height, exact = !job.playlist))
                    addOption("--merge-output-format", "mp4")
                    addOption("--remux-video", "mp4")
                }
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
                addOption(if (job.playlist) "--yes-playlist" else "--no-playlist")
                val name = if (job.playlist) "%(playlist_title)s/%(title)s.%(ext)s" else "%(title)s.%(ext)s"
                addOption("-o", work.absolutePath + "/" + name)
            }
            var item = ""
            val id = UUID.randomUUID().toString()
            processId = id
            val response = YoutubeDL.getInstance().execute(request, id) { progress, eta, line ->
                Regex("""Downloading item (\d+) of (\d+)""").find(line)?.let { m ->
                    item = "$noun ${m.groupValues[1]} of ${m.groupValues[2]}  ·  "
                }
                val title = Regex("""Destination: .*/([^/]+)\.[^./]+$""").find(line)?.groupValues?.get(1)
                val left = if (eta > 0) "  ·  ${eta / 60}:${"%02d".format(eta % 60)} left" else ""
                val pct = progress.coerceIn(0f, 100f)
                Downloads.progress(job.kind, pct / 100f, "$item${pct.toInt()}%$left", title)
                updateNotification(title ?: "Downloading...", pct.toInt())
            }
            // which JavaScript runtime solved YouTube's challenges (checked by the self-test)
            response.out.lines().filter { "jsc" in it || "JS challenge" in it }.forEach { Log.i(TAG, "ytdlp $it") }
        } catch (e: Exception) {
            if (stopRequested || e is YoutubeDL.CanceledException) {
                result = "stopped"
                message = "Stopped."
            } else {
                result = "failed"
                message = "Failed."
                Downloads.log(job.kind, "Error: ${e.message?.lines()?.lastOrNull { it.isNotBlank() } ?: e}")
            }
        } finally {
            processId = null
            // save every finished file, even after a stop part-way through a playlist
            val ext = if (job.kind == Kind.MUSIC) "mp3" else "mp4"
            work.walkTopDown().filter { it.isFile && it.extension.equals(ext, ignoreCase = true) }.forEach { file ->
                val subfolder = file.parentFile?.takeIf { it != work }?.name
                try {
                    when (MediaSaver.save(this, file, subfolder)) {
                        MediaSaver.Result.SAVED -> { saved++; Downloads.log(job.kind, "✓  ${file.nameWithoutExtension}") }
                        MediaSaver.Result.ALREADY_THERE -> { skipped++; Downloads.log(job.kind, "Already in your folder: ${file.nameWithoutExtension}") }
                    }
                } catch (e: Exception) {
                    Downloads.log(job.kind, "Couldn't save ${file.name}: ${e.message}")
                }
            }
            work.deleteRecursively()
            if (result == "ok" && saved == 0 && skipped == 0) {
                result = "failed"
                message = "Nothing was downloaded."
            }
            Downloads.finish(job.kind, result, message, saved, skipped)
        }
    }

    override fun onDestroy() {
        processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String, percent: Int, indeterminate: Boolean = false) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kimp)
            .setContentTitle("KIMP")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, indeterminate)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(
                0, "Stop",
                PendingIntent.getService(
                    this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .apply {
                if (Downloads.queue.value.isNotEmpty()) addAction(
                    0, "Stop all",
                    PendingIntent.getService(
                        this@DownloadService, 2, Intent(this@DownloadService, DownloadService::class.java).setAction(ACTION_STOP_ALL),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            .build()

    private fun updateNotification(text: String, percent: Int) {
        val waiting = Downloads.queue.value.size
        val line = if (waiting > 0) "$text  ·  $waiting waiting" else text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(line, percent))
    }
}
