package com.kimgdecierdo.kimp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.kimgdecierdo.kimp.engine.Engine
import com.kimgdecierdo.kimp.engine.Job
import com.kimgdecierdo.kimp.engine.Kind
import com.kimgdecierdo.kimp.engine.TAG
import com.kimgdecierdo.kimp.engine.probeQualities
import com.kimgdecierdo.kimp.service.DownloadService
import kotlinx.coroutines.launch

/**
 * Debug-only self-test, the Android twin of the Windows app's --selftest. Results go to logcat (tag KIMP):
 *   adb shell am start -n com.kimgdecierdo.kimp/.SelfTestActivity --es mode mp3 --es url <youtube-url>
 * mode: mp3 | playlist | probe | video:<height> | queue (an MP3 then a 240p video, queued back to back)
 */
class SelfTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val mode = intent.getStringExtra("mode") ?: "mp3"
        Log.i(TAG, "selftest start mode=$mode url=$url quickjs=${Engine.quickJs(this).exists()}")
        when {
            mode == "probe" -> (application as KimpApp).scope.launch {
                try {
                    val result = probeQualities(applicationContext, url)
                    Log.i(TAG, "probe ok \"${result.title}\": " + result.options.joinToString { it.label })
                } catch (e: Exception) {
                    Log.i(TAG, "probe failed ${e.message}")
                }
            }
            mode == "mp3" -> DownloadService.enqueue(this, Job(Kind.MUSIC, url, playlist = false), url)
            mode == "playlist" -> DownloadService.enqueue(this, Job(Kind.MUSIC, url, playlist = true), url)
            mode.startsWith("video:") -> {
                val height = mode.substringAfter(':').toInt()
                DownloadService.enqueue(this, Job(Kind.VIDEO, url, playlist = false, height = height, label = "${height}p"), url)
            }
            mode == "queue" -> {
                DownloadService.enqueue(this, Job(Kind.MUSIC, url, playlist = false), "selftest mp3")
                DownloadService.enqueue(this, Job(Kind.VIDEO, url, playlist = false, height = 240, label = "240p"), "selftest video")
            }
            else -> Log.i(TAG, "selftest unknown mode $mode")
        }
        finish()
    }
}
