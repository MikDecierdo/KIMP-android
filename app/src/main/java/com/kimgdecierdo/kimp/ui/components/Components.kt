package com.kimgdecierdo.kimp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kimgdecierdo.kimp.engine.PanelState
import com.kimgdecierdo.kimp.ui.theme.Kimp
import com.kimgdecierdo.kimp.ui.theme.KimpFonts

/** Small uppercase mono label (the site's .hud-label). */
@Composable
fun Hud(text: String, modifier: Modifier = Modifier, color: Color = Kimp.colors.muted, maxLines: Int = 1) {
    Text(
        text.uppercase(), modifier, color = color, fontFamily = KimpFonts.mono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.15.em, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Kimp.colors.ink, size: Int = 14) {
    Text(text, modifier, color = color, fontFamily = KimpFonts.sans, fontSize = size.sp, lineHeight = (size * 1.5).sp)
}

@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Kimp.colors.line))
}

/** Inverted block with bold uppercase mono text; 80% opacity while pressed. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Kimp.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .height(48.dp)
            .background(c.invertBg.copy(alpha = if (!enabled) 0.35f else if (pressed) 0.8f else 1f))
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(), color = c.invertFg, fontFamily = KimpFonts.mono, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, letterSpacing = 0.12.em, maxLines = 1,
        )
    }
}

/** Outlined block that flips to inverted while pressed (or when `active`). */
@Composable
fun SecondaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    color: Color = Kimp.colors.ink, active: Boolean = false,
) {
    val c = Kimp.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val on = enabled && (pressed || active)
    Box(
        modifier
            .height(48.dp)
            .background(if (on) c.invertBg else c.bg)
            .border(1.dp, if (on) c.invertBg else c.lineStrong)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(), color = if (!enabled) c.faint else if (on) c.invertFg else color,
            fontFamily = KimpFonts.mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.12.em, maxLines = 1,
        )
    }
}

/** Small label like "LINK" between two rules. */
@Composable
fun SectionStrip(title: String) {
    Column {
        Rule()
        Hud(title, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp))
        Rule()
    }
}

/** "Download *music.*": Archivo with an italic Source Serif accent word. */
@Composable
fun Headline(plain: String, italic: String) {
    val c = Kimp.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = KimpFonts.display, fontWeight = FontWeight.Medium, color = c.inkStrong)) { append(plain) }
            withStyle(SpanStyle(fontFamily = KimpFonts.serif, fontStyle = FontStyle.Italic, color = c.muted)) {
                append(if (plain.isEmpty()) italic else " $italic")
            }
        },
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 20.dp),
        fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.03).em,
    )
}

@Composable
fun LinkField(value: String, onValueChange: (String) -> Unit, onGo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Kimp.colors
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(fontFamily = KimpFonts.sans, fontSize = 15.sp, color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier.height(48.dp).background(c.raised).border(1.dp, c.lineStrong),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("https://www.youtube.com/watch?v=...", color = c.faint, fontFamily = KimpFonts.sans, fontSize = 15.sp, maxLines = 1)
                }
                inner()
            }
        },
    )
}

data class Tile(val title: String, val hint: String = "")

/** Ruled grid of cells sharing 1dp dividers; the selected cell is fully inverted. */
@Composable
fun TileGrid(tiles: List<Tile>, selected: Int?, columns: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val c = Kimp.colors
    Column(Modifier.fillMaxWidth().background(c.line), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        tiles.withIndex().chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { (index, tile) ->
                    val on = index == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (on) c.invertBg else c.bg)
                            .clickable(enabled = enabled) { onSelect(index) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            tile.title, color = if (on) c.invertFg else c.inkStrong,
                            fontFamily = KimpFonts.display, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                        )
                        if (tile.hint.isNotEmpty()) {
                            Text(
                                tile.hint, color = if (on) c.invertMuted else c.muted, fontFamily = KimpFonts.sans,
                                fontSize = 12.sp, lineHeight = 16.sp,
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f).fillMaxHeight().background(c.bg)) }
            }
        }
    }
}

/** Title, thin progress bar, status line and activity log for one tab; hidden until something happens. */
@Composable
fun ProgressPanel(state: PanelState) {
    if (state == PanelState()) return
    val c = Kimp.colors
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            state.title, color = c.inkStrong, fontFamily = KimpFonts.display, fontWeight = FontWeight.Medium,
            fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).background(c.line)) {
            Box(Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
        }
        Hud(state.status, Modifier.padding(top = 8.dp))
        if (state.log.isNotEmpty()) Column(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .background(c.raised)
                .border(1.dp, c.line)
                .padding(12.dp),
        ) {
            state.log.takeLast(60).forEach { Body(it, size = 13) }
        }
    }
}
