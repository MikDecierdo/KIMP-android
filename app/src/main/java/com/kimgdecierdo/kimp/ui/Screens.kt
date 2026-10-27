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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimgdecierdo.kimp.Prefs
import com.kimgdecierdo.kimp.engine.Downloads
import com.kimgdecierdo.kimp.engine.Engine
import com.kimgdecierdo.kimp.engine.Kind
import com.kimgdecierdo.kimp.ui.components.Body
import com.kimgdecierdo.kimp.ui.components.Headline
import com.kimgdecierdo.kimp.ui.components.Hud
import com.kimgdecierdo.kimp.ui.components.LinkField
import com.kimgdecierdo.kimp.ui.components.PrimaryButton
import com.kimgdecierdo.kimp.ui.components.ProgressPanel
import com.kimgdecierdo.kimp.ui.components.SecondaryButton
import com.kimgdecierdo.kimp.ui.components.SectionStrip
import com.kimgdecierdo.kimp.ui.components.Tile
import com.kimgdecierdo.kimp.ui.components.TileGrid
import com.kimgdecierdo.kimp.ui.theme.Kimp
import com.kimgdecierdo.kimp.ui.theme.KimpFonts

private const val VERSION = "1.0"
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
fun MusicScreen(vm: MainViewModel) {
    val c = Kimp.colors
    val panel by Downloads.panel(Kind.MUSIC).collectAsState()
    val busy by Downloads.busy.collectAsState()
    Page {
        Headline(
            "Download", "music.", "KIMP / YouTube → MP3",
            "Paste a link, pick song or playlist, download. 320 kbps MP3 with title, artist and cover art, saved to Music/KIMP.",
        )
        SectionStrip("01", "Link", "Any YouTube URL")
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkField(vm.musicUrl, { vm.musicUrl = it }, vm::startMusic, Modifier.weight(1f))
            PasteButton { vm.musicUrl = it }
        }
        SectionStrip("02", "What to download", "Pick one")
        TileGrid(
            listOf(
                Tile("Song", "Just this song, even if the link is from a playlist."),
                Tile("Playlist", "Every song in the playlist, in a folder of its own."),
            ),
            selected = if (vm.musicPlaylist) 1 else 0, columns = 2, enabled = busy == null,
        ) { vm.musicPlaylist = it == 1 }
        SectionStrip("03", "Save & download", "Music/KIMP")
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Stop", vm::stop, enabled = busy == Kind.MUSIC, color = c.danger)
            PrimaryButton("Download MP3 ↓", vm::startMusic, Modifier.weight(1f), enabled = busy == null && vm.musicUrl.isNotBlank())
        }
        SectionStrip("04", "Progress", "Activity")
        ProgressPanel(panel)
    }
}

@Composable
fun VideoScreen(vm: MainViewModel) {
    val c = Kimp.colors
    val panel by Downloads.panel(Kind.VIDEO).collectAsState()
    val busy by Downloads.busy.collectAsState()
    Page {
        Headline(
            "Download", "video.", "KIMP / YouTube → MP4",
            "Paste a link, find the qualities it has, pick one. Saved as MP4 with title and cover art in Movies/KIMP.",
        )
        SectionStrip("01", "Link", "One video")
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkField(vm.videoUrl, { vm.videoUrl = it }, vm::findQualities, Modifier.weight(1f))
            PasteButton { vm.videoUrl = it }
        }
        SecondaryButton(
            "Find qualities", vm::findQualities, Modifier.fillMaxWidth().padding(16.dp),
            enabled = busy == null && vm.probe !is MainViewModel.Probe.Loading,
        )
        SectionStrip("02", "Quality", "Only what the video has")
        when (val probe = vm.probe) {
            MainViewModel.Probe.Idle -> Hud("Paste a link, then press Find qualities", Modifier.padding(16.dp))
            MainViewModel.Probe.Loading -> Hud("Finding qualities...", Modifier.padding(16.dp))
            is MainViewModel.Probe.Failed -> Body(probe.message, Modifier.padding(16.dp), color = c.danger)
            is MainViewModel.Probe.Ready -> {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        probe.result.title, color = c.inkStrong, fontFamily = KimpFonts.display,
                        fontWeight = FontWeight.Medium, fontSize = 17.sp, maxLines = 2,
                    )
                    Hud(probe.result.detail, Modifier.padding(top = 4.dp))
                }
                TileGrid(
                    probe.result.options.map { Tile(it.label, it.hint) },
                    selected = vm.quality, columns = 3, enabled = busy == null,
                ) { vm.quality = it }
            }
        }
        SectionStrip("03", "Save & download", "Movies/KIMP")
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Stop", vm::stop, enabled = busy == Kind.VIDEO, color = c.danger)
            PrimaryButton(
                "Download MP4 ↓", vm::startVideo, Modifier.weight(1f),
                enabled = busy == null && vm.probe is MainViewModel.Probe.Ready && vm.quality != null,
            )
        }
        SectionStrip("04", "Progress", "Activity")
        ProgressPanel(panel)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val c = Kimp.colors
    val context = LocalContext.current
    val theme by Prefs.theme.collectAsState()
    val status by Engine.status.collectAsState()
    Page {
        Headline("Your", "settings.", "KIMP / Preferences", "Choose how KIMP looks and keep its download engine up to date.")
        SectionStrip("01", "Appearance", "System, light or dark")
        Row(Modifier.padding(16.dp)) {
            listOf("System", "Light", "Dark").forEach { value ->
                SecondaryButton(value, { vm.setTheme(value) }, Modifier.weight(1f), active = theme == value)
            }
        }
        SectionStrip("02", "Download engine", "yt-dlp")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val engine = when (val s = status) {
                Engine.Status.Starting -> "Starting..."
                Engine.Status.Updating -> "Updating yt-dlp..."
                is Engine.Status.Ready -> "yt-dlp ${s.ytdlpVersion ?: "ready"}"
                is Engine.Status.Failed -> "Engine error: ${s.message}"
            }
            Body(engine)
            Body(
                if (Engine.quickJs(context).exists()) "JavaScript runtime: QuickJS-ng (bundled)"
                else "JavaScript runtime: missing, so YouTube downloads may fail",
                color = c.muted, size = 13,
            )
            vm.engineMessage?.let { Hud(it, color = c.accentInk, maxLines = 2) }
            SecondaryButton(
                "Update yt-dlp", vm::updateEngine, Modifier.fillMaxWidth().padding(top = 6.dp),
                enabled = status is Engine.Status.Ready,
            )
            Body("YouTube changes often. If downloads start failing, update yt-dlp.", color = c.muted, size = 13)
        }
        SectionStrip("03", "Where files go", "Shared storage")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Body("Music  →  Music/KIMP")
            Body("Videos  →  Movies/KIMP")
            Body("They show up in your music player and gallery.", color = c.muted, size = 13)
        }
    }
}

private val RESPONSIBLE_USE = listOf(
    "Download only what you own, what's free to download (Creative Commons or copyright-free channels), or what you have permission to save.",
    "Downloading copyrighted songs or videos without permission can break copyright law and YouTube's Terms of Service.",
    "Keep downloads for personal use. Don't re-upload, sell or share content you don't have the rights to.",
    "KIMP doesn't unlock paid or protected content.",
)

private val TERMS = listOf(
    "KIMP is provided \"as is\", without warranty of any kind. The developer is not liable for any damages or for how KIMP is used.",
    "KIMP is not affiliated with, endorsed or sponsored by YouTube or Google. All trademarks belong to their owners.",
    "You agree to follow copyright law, YouTube's Terms of Service and the laws where you live.",
    "Privacy: no accounts, no tracking, no personal data collected. Settings stay on this phone, and KIMP only connects to YouTube to get what you ask for.",
    "KIMP for Android is free software under the GNU GPL v3. Its source code and the licenses of the parts it uses are linked below.",
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
        Headline("About", "KIMP.", "KIMP / About", "YouTube to MP3 and MP4, for Android.")
        SectionStrip("01", "Developer", "Version $VERSION")
        Column(Modifier.padding(16.dp)) {
            Text(DEVELOPER, color = c.inkStrong, fontFamily = KimpFonts.display, fontWeight = FontWeight.Medium, fontSize = 24.sp)
            Hud("Developer  ·  KIMP", color = c.accentInk)
            Body("Designed and built by $DEVELOPER. © 2026.", Modifier.padding(top = 6.dp), color = c.muted, size = 13)
        }
        SectionStrip("02", "Responsible use", "Please read")
        Body(
            "KIMP is a tool for saving music and videos you're allowed to save. Whether a download is legal depends on who owns it and the laws where you live.",
            Modifier.padding(16.dp),
        )
        NumberedList(RESPONSIBLE_USE)
        Hud("You are responsible for how you use KIMP.", Modifier.padding(16.dp), color = c.danger, maxLines = 2)
        SectionStrip("03", "Terms of use", "Read before use")
        Column(Modifier.padding(top = 16.dp)) { NumberedList(TERMS) }
        SectionStrip("04", "Open source", "GNU GPL v3")
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
