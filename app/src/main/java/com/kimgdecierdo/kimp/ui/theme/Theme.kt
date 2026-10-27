package com.kimgdecierdo.kimp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.kimgdecierdo.kimp.R

/** The KIMP palette: the same tokens as the website (getartcraft.com-style paper and ink). */
@Immutable
data class KimpColors(
    val bg: Color, val raised: Color, val sunken: Color,
    val ink: Color, val inkStrong: Color, val muted: Color, val faint: Color,
    val line: Color, val lineStrong: Color,
    val invertBg: Color, val invertFg: Color, val invertMuted: Color,
    val accent: Color, val accentInk: Color, val danger: Color,
)

val LightColors = KimpColors(
    bg = Color(0xFFF2F1EE), raised = Color(0xFFFAF9F7), sunken = Color(0xFFE9E8E4),
    ink = Color(0xFF101014), inkStrong = Color(0xFF000000), muted = Color(0xFF666667), faint = Color(0xFF939392),
    line = Color(0xFFCECDCB), lineStrong = Color(0xFF989797),
    invertBg = Color(0xFF101014), invertFg = Color(0xFFF2F1EE), invertMuted = Color(0xFF9C9C9B),
    accent = Color(0xFF2D81FF), accentInk = Color(0xFF1659C4), danger = Color(0xFFB3261E),
)

val DarkColors = KimpColors(
    bg = Color(0xFF121316), raised = Color(0xFF101014), sunken = Color(0xFF060607),
    ink = Color(0xFFF2F1EE), inkStrong = Color(0xFFFFFFFF), muted = Color(0xFF989897), faint = Color(0xFF676768),
    line = Color(0xFF333436), lineStrong = Color(0xFF6C6C6C),
    invertBg = Color(0xFFFFFFFF), invertFg = Color(0xFF0B0B0C), invertMuted = Color(0xFF686868),
    accent = Color(0xFF2D81FF), accentInk = Color(0xFF74AAFF), danger = Color(0xFFFF6259),
)

/** The same five fonts as the Windows app and website (all SIL Open Font License). */
object KimpFonts {
    val sans = FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium))
    val display = FontFamily(Font(R.font.archivo_medium, FontWeight.Medium))
    val mono = FontFamily(Font(R.font.geist_mono_medium, FontWeight.Medium), Font(R.font.geist_mono_bold, FontWeight.Bold))
    val serif = FontFamily(Font(R.font.source_serif_italic, FontWeight.Normal, FontStyle.Italic))
    val brand = FontFamily(Font(R.font.carter_one))
}

private val LocalKimpColors = staticCompositionLocalOf { LightColors }

object Kimp {
    val colors: KimpColors
        @Composable get() = LocalKimpColors.current
}

@Composable
fun KimpTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKimpColors provides if (dark) DarkColors else LightColors, content = content)
}
