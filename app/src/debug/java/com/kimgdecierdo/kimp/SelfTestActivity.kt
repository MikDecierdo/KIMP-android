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
 * mode: mp3 | playlist | probe | video:<height>
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
            mode == "mp3" -> DownloadService.start(this, Job(Kind.MUSIC, url, playlist = false))
            mode == "playlist" -> DownloadService.start(this, Job(Kind.MUSIC, url, playlist = true))
            mode.startsWith("video:") -> {
                val height = mode.substringAfter(':').toInt()
                DownloadService.start(this, Job(Kind.VIDEO, url, playlist = false, height = height, label = "${height}p"))
            }
            else -> Log.i(TAG, "selftest unknown mode $mode")
        }
        finish()
    }
}
