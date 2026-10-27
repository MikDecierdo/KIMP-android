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

/** Runs one download in the foreground, so it keeps going with the screen off. */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.kimgdecierdo.kimp.STOP"

        fun start(context: Context, job: Job) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra("kind", job.kind.name)
                .putExtra("url", job.url)
                .putExtra("playlist", job.playlist)
                .putExtra("height", job.height ?: -1)
                .putExtra("label", job.label)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var processId: String? = null
    @Volatile private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
            return START_NOT_STICKY
        }
        val job = intent?.let {
            Job(
                kind = Kind.valueOf(it.getStringExtra("kind") ?: return@let null),
                url = it.getStringExtra("url") ?: return@let null,
                playlist = it.getBooleanExtra("playlist", false),
                height = it.getIntExtra("height", -1).takeIf { h -> h > 0 },
                label = it.getStringExtra("label") ?: "",
            )
        }
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Starting...", 0, indeterminate = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (job == null || !Downloads.tryBegin(job)) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        stopRequested = false
        scope.launch {
            run(job)
            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
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
            .build()

    private fun updateNotification(text: String, percent: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, percent))
    }
}
