package com.kimgdecierdo.kimp.engine

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.min

data class QualityOption(val height: Int, val label: String, val hint: String)
data class ProbeResult(val url: String, val title: String, val detail: String, val options: List<QualityOption>)

fun qualityName(lines: Int): String = when {
    lines <= 240 -> "Low"
    lines <= 480 -> "SD"
    lines <= 720 -> "HD"
    lines <= 1080 -> "Full HD"
    lines <= 1440 -> "2K"
    lines <= 2160 -> "4K"
    else -> "8K"
}

/** Finds the resolutions a video really has (ported from the Windows app's probe_qualities). */
suspend fun probeQualities(context: Context, url: String): ProbeResult = withContext(Dispatchers.IO) {
    Engine.awaitReady()
    val request = Engine.request(context, url).apply {
        addOption("--dump-single-json")
        addOption("--no-playlist")
        addOption("--skip-download")
    }
    val info = JSONObject(YoutubeDL.getInstance().execute(request).out)
    if (info.optString("_type") == "playlist") {
        throw IllegalArgumentException("That's a playlist link. Paste a link to one video.")
    }
    val duration = info.optDouble("duration", 0.0)
    val formats = info.optJSONArray("formats") ?: throw IllegalArgumentException("No video qualities found for that link.")
    val all = (0 until formats.length()).map { formats.getJSONObject(it) }

    fun str(f: JSONObject, key: String): String? = if (f.isNull(key)) null else f.optString(key)
    fun size(f: JSONObject): Long {
        // YouTube often leaves out the size of video streams; bitrate x duration is close enough
        val exact = f.optLong("filesize", 0L).takeIf { it > 0 } ?: f.optLong("filesize_approx", 0L).takeIf { it > 0 }
        return exact ?: (f.optDouble("tbr", 0.0) * 125 * duration).toLong()
    }

    val audio = all.filter { str(it, "vcodec") == "none" && str(it, "acodec") !in listOf(null, "none") }
    val m4a = audio.filter { str(it, "ext") == "m4a" }.ifEmpty { audio }
    val audioSize = m4a.maxByOrNull { it.optDouble("abr", it.optDouble("tbr", 0.0)) }?.let(::size) ?: 0L

    val byHeight = all
        .filter { str(it, "vcodec") !in listOf(null, "none") && it.optInt("height", 0) > 0 }
        .groupBy { it.optInt("height") }

    val options = byHeight.keys.sorted().map { height ->
        val group = byHeight.getValue(height)
        val avc = group.filter { str(it, "vcodec")?.startsWith("avc1") == true }
        val pick = avc.ifEmpty { group }.maxBy { it.optDouble("tbr", 0.0) }
        // name by the shorter side, so a vertical 1080x1920 Short reads "1080p"
        val lines = min(height, pick.optInt("width", height).takeIf { it > 0 } ?: height)
        var hint = qualityName(lines)
        if (group.maxOf { it.optDouble("fps", 0.0) } >= 50) hint += " · 60 fps"
        val videoSize = size(pick)
        if (videoSize > 0) {
            val total = videoSize + if (str(pick, "acodec") in listOf(null, "none")) audioSize else 0L
            hint += "\n≈ ${"%,d".format(total / 1_000_000)} MB"
        }
        QualityOption(height, "${lines}p", hint)
    }
    if (options.isEmpty()) throw IllegalArgumentException("No video qualities found for that link.")

    val detail = listOfNotNull(
        str(info, "channel") ?: str(info, "uploader"),
        str(info, "duration_string"),
    ).joinToString(" · ")
    ProbeResult(url, str(info, "title") ?: "Video", detail, options)
}

/** Default pick: 1080p, else the best at or below 1080p, else the lowest. */
fun defaultQuality(options: List<QualityOption>): Int {
    options.indexOfFirst { it.label == "1080p" }.takeIf { it >= 0 }?.let { return it }
    val below = options.indices.filter { options[it].label.removeSuffix("p").toIntOrNull()?.let { h -> h <= 1080 } == true }
    return below.lastOrNull() ?: 0
}
