package com.kimgdecierdo.kimp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimgdecierdo.kimp.BuildConfig
import com.kimgdecierdo.kimp.Prefs
import com.kimgdecierdo.kimp.engine.Downloads
import com.kimgdecierdo.kimp.engine.Engine
import com.kimgdecierdo.kimp.engine.Kind
import com.kimgdecierdo.kimp.engine.Updater
import com.kimgdecierdo.kimp.ui.components.Body
import com.kimgdecierdo.kimp.ui.components.Headline
import com.kimgdecierdo.kimp.ui.components.Hud
import com.kimgdecierdo.kimp.ui.components.LinkField
import com.kimgdecierdo.kimp.ui.components.PrimaryButton
import com.kimgdecierdo.kimp.ui.components.ProgressPanel
import com.kimgdecierdo.kimp.ui.components.Rule
import com.kimgdecierdo.kimp.ui.components.SecondaryButton
import com.kimgdecierdo.kimp.ui.components.SectionStrip
import com.kimgdecierdo.kimp.ui.components.Tile
import com.kimgdecierdo.kimp.ui.components.TileGrid
import com.kimgdecierdo.kimp.ui.theme.Kimp
import com.kimgdecierdo.kimp.ui.theme.KimpFonts
import kotlinx.coroutines.delay

private val VERSION = BuildConfig.VERSION_NAME
private const val DEVELOPER = "Kim G. Decierdo"
private const val SOURCE_URL = "https://github.com/MikDecierdo/KIMP-android"

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
}

@Composable
private fun PasteButton(onText: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    SecondaryButton("Paste", { clipboard.getText()?.text?.trim()?.let(onText) })
}

@Composable
private fun SavesTo(folder: String) {
    Hud("Saves to $folder", Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), color = Kimp.colors.faint)
}

@Composable
fun MusicScreen(vm: MainViewModel) {
    val c = Kimp.colors
    val panel by Downloads.panel(Kind.MUSIC).collectAsState()
    val busy by Downloads.busy.collectAsState()
    Page {
        Headline("Download", "music.")
        SectionStrip("Link")
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkField(vm.musicUrl, { vm.musicUrl = it }, vm::startMusic, Modifier.weight(1f))
            PasteButton { vm.musicUrl = it }
        }
        SectionStrip("Type")
        TileGrid(
            listOf(Tile("Song"), Tile("Playlist")),
            selected = if (vm.musicPlaylist) 1 else 0, columns = 2, enabled = true,
        ) { vm.musicPlaylist = it == 1 }
        Rule()
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Stop", vm::stop, enabled = busy == Kind.MUSIC, color = c.danger)
            PrimaryButton("Download MP3 ↓", vm::startMusic, Modifier.weight(1f), enabled = vm.musicUrl.isNotBlank())
        }
        SavesTo("Music/KIMP")
        QueueNote(vm.musicNote) { vm.clearNote(Kind.MUSIC) }
        ProgressPanel(panel)
        Queue(vm)
    }
}

@Composable
fun VideoScreen(vm: MainViewModel) {
    val c = Kimp.colors
    val panel by Downloads.panel(Kind.VIDEO).collectAsState()
    val busy by Downloads.busy.collectAsState()
    Page {
        Headline("Download", "video.")
        SectionStrip("Link")
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkField(vm.videoUrl, { vm.videoUrl = it }, vm::findQualities, Modifier.weight(1f))
            PasteButton { vm.videoUrl = it }
        }
        SecondaryButton(
            "Find qualities", vm::findQualities, Modifier.fillMaxWidth().padding(16.dp),
            enabled = vm.probe !is MainViewModel.Probe.Loading,
        )
        when (val probe = vm.probe) {
            MainViewModel.Probe.Idle -> Unit
            MainViewModel.Probe.Loading -> {
                SectionStrip("Quality")
                Hud("Finding qualities...", Modifier.padding(16.dp))
            }
            is MainViewModel.Probe.Failed -> {
                SectionStrip("Quality")
                Body(probe.message, Modifier.padding(16.dp), color = c.danger)
            }
            is MainViewModel.Probe.Ready -> {
                SectionStrip("Quality")
                Text(
                    probe.result.title, Modifier.padding(16.dp), color = c.inkStrong, fontFamily = KimpFonts.display,
                    fontWeight = FontWeight.Medium, fontSize = 17.sp, maxLines = 2,
                )
                TileGrid(
                    probe.result.options.map { Tile(it.label, it.hint) },
                    selected = vm.quality, columns = 3, enabled = true,
                ) { vm.quality = it }
            }
        }
        Rule()
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Stop", vm::stop, enabled = busy == Kind.VIDEO, color = c.danger)
            PrimaryButton(
                "Download MP4 ↓", vm::startVideo, Modifier.weight(1f),
                enabled = vm.probe is MainViewModel.Probe.Ready && vm.quality != null,
            )
        }
        SavesTo("Movies/KIMP")
        QueueNote(vm.videoNote) { vm.clearNote(Kind.VIDEO) }
        ProgressPanel(panel)
        Queue(vm)
    }
}

/** "Added to queue" for a few seconds under the Download button. */
@Composable
private fun QueueNote(note: String?, onTimeout: () -> Unit) {
    if (note == null) return
    LaunchedEffect(note) {
        delay(3000)
        onTimeout()
    }
    Hud(note, Modifier.padding(start = 16.dp, bottom = 12.dp), color = Kimp.colors.accentInk)
}

/** The waiting downloads (music and video share one queue), each removable. */
@Composable
private fun Queue(vm: MainViewModel) {
    val c = Kimp.colors
    val items by Downloads.queue.collectAsState()
    if (items.isEmpty()) return
    SectionStrip("Queue  ·  ${items.size}")
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hud(if (item.job.kind == Kind.MUSIC) "MP3" else "MP4", Modifier.width(40.dp), color = c.faint)
                Text(
                    item.name, Modifier.weight(1f), color = c.ink, fontFamily = KimpFonts.sans, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "✕", Modifier.clickable { vm.removeFromQueue(item.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = c.muted, fontSize = 16.sp,
                )
            }
        }
        SecondaryButton("Clear queue", vm::clearQueue, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp))
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val c = Kimp.colors
    val context = LocalContext.current
    val theme by Prefs.theme.collectAsState()
    val status by Engine.status.collectAsState()
    val update by Updater.state.collectAsState()
    Page {
        Headline("", "Settings.")
        SectionStrip("Theme")
        Row(Modifier.padding(16.dp)) {
            listOf("System", "Light", "Dark").forEach { value ->
                SecondaryButton(value, { vm.setTheme(value) }, Modifier.weight(1f), active = theme == value)
            }
        }
        SectionStrip("Engine")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val engine = when (val s = status) {
                Engine.Status.Starting -> "Starting..."
                Engine.Status.Updating -> "Updating yt-dlp..."
                is Engine.Status.Ready -> "yt-dlp ${s.ytdlpVersion ?: "ready"}"
                is Engine.Status.Failed -> "Engine error: ${s.message}"
            }
            Body(engine)
            if (!Engine.quickJs(context).exists()) Body("JavaScript runtime missing", color = c.danger, size = 13)
            vm.engineMessage?.let { Hud(it, color = c.accentInk, maxLines = 2) }
            SecondaryButton(
                "Update yt-dlp", vm::updateEngine, Modifier.fillMaxWidth().padding(top = 6.dp),
                enabled = status is Engine.Status.Ready,
            )
        }
        SectionStrip("App")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Body("KIMP $VERSION")
            when (val u = update) {
                Updater.State.Checking -> Hud("Checking...")
                Updater.State.UpToDate -> Hud("Up to date")
                is Updater.State.Failed -> Hud(u.message, color = c.danger)
                is Updater.State.Available -> Hud("${u.release.version} is available", color = c.accentInk)
                is Updater.State.Downloading -> Hud("Downloading  ${(u.progress * 100).toInt()}%", color = c.accentInk)
                Updater.State.Idle -> Unit
            }
            val available = update as? Updater.State.Available
            SecondaryButton(
                if (available != null) "Update to ${available.release.version}" else "Check for updates",
                { if (available != null) vm.installUpdate(available.release) else vm.checkUpdate() },
                Modifier.fillMaxWidth().padding(top = 6.dp),
                enabled = update !is Updater.State.Checking && update !is Updater.State.Downloading,
            )
        }
    }
}

private val RESPONSIBLE_USE = listOf(
    "Only download what you own, what's free, or what you have permission to save.",
    "Downloading copyrighted content without permission can break the law and YouTube's terms.",
    "Personal use only. Don't re-upload, sell or share it.",
)

private val TERMS = listOf(
    "Provided \"as is\", without warranty. The developer isn't liable for how KIMP is used.",
    "Not affiliated with YouTube or Google.",
    "No accounts, no tracking, no data collected.",
    "Free software under the GNU GPL v3.",
)

@Composable
private fun NumberedList(items: List<String>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, text ->
            Row {
                Hud("%02d".format(i + 1), Modifier.width(32.dp).padding(top = 3.dp), color = Kimp.colors.faint)
                Body(text, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val c = Kimp.colors
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var openLicense by rememberSaveable { mutableStateOf<String?>(null) }
    openLicense?.let { name ->
        LicenseText(name) { openLicense = null }
        return
    }
    Page {
        Headline("About", "KIMP.")
        SectionStrip("Developer")
        Column(Modifier.padding(16.dp)) {
            Text(DEVELOPER, color = c.inkStrong, fontFamily = KimpFonts.display, fontWeight = FontWeight.Medium, fontSize = 24.sp)
            Hud("Version $VERSION  ·  © 2026", color = c.accentInk)
        }
        SectionStrip("Responsible use")
        Column(Modifier.padding(top = 16.dp)) { NumberedList(RESPONSIBLE_USE) }
        Hud("You are responsible for how you use KIMP.", Modifier.padding(16.dp), color = c.danger, maxLines = 2)
        SectionStrip("Terms of use")
        Column(Modifier.padding(vertical = 16.dp)) { NumberedList(TERMS) }
        SectionStrip("Open source")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Source code", { uri.openUri(SOURCE_URL) }, Modifier.fillMaxWidth())
            Hud("Licenses", Modifier.padding(top = 8.dp))
            remember { context.assets.list("licenses")?.sorted().orEmpty() }.forEach { name ->
                Text(
                    name.removeSuffix(".txt"),
                    Modifier.fillMaxWidth().clickable { openLicense = name }.padding(vertical = 8.dp),
                    color = c.accentInk, fontFamily = KimpFonts.sans, fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun LicenseText(name: String, onBack: () -> Unit) {
    val c = Kimp.colors
    val context = LocalContext.current
    val text = remember(name) { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        SecondaryButton("← Back", onBack, Modifier.fillMaxWidth().padding(16.dp))
        Hud(name.removeSuffix(".txt"), Modifier.padding(horizontal = 16.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(text, color = c.ink, fontFamily = KimpFonts.mono, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
