package com.eymen.os

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Colour tokens from the EYMEN Uydu 2.0 design: night blue ground, champagne accent, one focus. */
object C {
    val Ink = Color(0xFF05080F)
    val Navy = Color(0xFF0A1224)
    val Deep = Color(0xFF070B16)
    val Panel = Color(0xF2070B16)
    val Gold = Color(0xFFE8C27A)
    val GoldInk = Color(0xFF1E1505)
    val GoldSoft = Color(0x1FE8C27A)
    val Fg = Color(0xFFF2F4F8)
    val Fg2 = Color(0xFFC9D0DD)
    val Mut = Color(0xFFA3ACBF)
    val Dim = Color(0xFF8C96AA)
    val Line = Color(0x1FD6E2FF)
    val Glass = Color(0x0FD6E2FF)
    val Glass2 = Color(0x1AD6E2FF)
    val Tile = Color(0xFF17213D)
    val Live = Color(0xFFFF5468)
    val Red = Color(0xFFE5484D)
    val Green = Color(0xFF3FB950)
    val Mint = Color(0xFF7BE08A)
    val Yellow = Color(0xFFE9C46A)
    val Blue = Color(0xFF4C8DFF)
    val FocusInk = Color(0xFF0A1224)
    val FocusSub = Color(0xFF3A4459)
    val LogoBg = Color(0xFFEEF1F6)
}

/** Sora (headings, clock, channel numbers) and Manrope (text). Falls back to the system face. */
object Fonts {
    private val body = HashMap<Int, FontFamily>()
    private val display = HashMap<Int, FontFamily>()
    private var signature: FontFamily? = null

    fun init(ctx: Context) {
        val names = try {
            ctx.assets.list("fonts")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        if (names.contains("Manrope.ttf")) {
            for (w in listOf(400, 500, 600, 700, 800)) load(ctx, "fonts/Manrope.ttf", w)?.let { body[w] = it }
        }
        if (names.contains("Sora.ttf")) {
            for (w in listOf(400, 500, 600, 700)) load(ctx, "fonts/Sora.ttf", w)?.let { display[w] = it }
        }
        if (names.contains("GreatVibes.ttf")) {
            signature = try {
                FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(ctx.assets, "fonts/GreatVibes.ttf")))
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun load(ctx: Context, path: String, w: Int): FontFamily? = try {
        val tf: Typeface? = Typeface.Builder(ctx.assets, path).setFontVariationSettings("'wght' $w").build()
        if (tf == null) null else FontFamily(androidx.compose.ui.text.font.Typeface(tf))
    } catch (e: Exception) {
        null
    }

    fun family(displayFace: Boolean, w: Int): FontFamily? = if (displayFace) display[w] else body[w]

    /** The hand-written face of the signature; TVs without the bundled font use their own cursive. */
    fun signature(): FontFamily = signature ?: FontFamily.Cursive
}

@Composable
fun T(
    text: String,
    size: Int,
    color: Color = C.Fg,
    weight: Int = 500,
    display: Boolean = false,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    spacing: Float = 0f,
    align: TextAlign = TextAlign.Start,
    lineHeight: Int = 0
) {
    val fam = Fonts.family(display, weight)
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = fam ?: FontFamily.SansSerif,
            fontWeight = if (fam == null) FontWeight(weight) else null,
            letterSpacing = spacing.sp,
            textAlign = align,
            lineHeight = if (lineHeight > 0) lineHeight.sp else TextUnit.Unspecified
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * The whole interface is laid out on a 1280×720 design grid (like the mock-ups);
 * 1.dp equals one design pixel whatever the TV's real resolution is.
 */
@Composable
fun DesignCanvas(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = constraints.maxWidth / 1280f
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            content()
        }
    }
}
