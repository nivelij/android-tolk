package dev.nivelij.tolk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.nivelij.tolk.R

@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val onSheet: Color,
    val onSheetMuted: Color,
    val danger: Color,
    val dark: Boolean,
)

private val Light = Palette(
    background = Color(0xFFECEEF2),
    surface = Color(0xFFF8F9FB),
    ink = Color(0xFF1C2333),
    muted = Color(0xFF687087),
    line = Color(0xFFD6DAE2),
    onSheet = Color(0xFFF6F7FA),
    onSheetMuted = Color(0xB3F6F7FA),
    danger = Color(0xFFB3261E),
    dark = false,
)

private val Dark = Palette(
    background = Color(0xFF141925),
    surface = Color(0xFF1D2331),
    ink = Color(0xFFE8EBF2),
    muted = Color(0xFF8D95A8),
    line = Color(0xFF2A3142),
    onSheet = Color(0xFFF6F7FA),
    onSheetMuted = Color(0xB3F6F7FA),
    danger = Color(0xFFFF8A80),
    dark = true,
)

val Atkinson = FontFamily(
    Font(R.font.atkinson_light, FontWeight.Light),
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_medium, FontWeight.Medium),
    Font(R.font.atkinson_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

@Immutable
data class Type(
    val wordmark: TextStyle,
    val title: TextStyle,
    val input: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val small: TextStyle,
)

private val TypeScale = Type(
    wordmark = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    title = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    input = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 30.sp),
    body = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    label = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    small = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
)

fun translationStyle(length: Int): TextStyle = when {
    length < 120 -> TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 39.sp, letterSpacing = (-0.3).sp)
    length < 400 -> TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 23.sp, lineHeight = 32.sp)
    else -> TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 27.sp)
}

val LocalPalette = staticCompositionLocalOf { Light }
val LocalType = staticCompositionLocalOf { TypeScale }

object Tolk {
    val colors: Palette @Composable get() = LocalPalette.current
    val type: Type @Composable get() = LocalType.current
}

@Composable
fun TolkTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides if (isSystemInDarkTheme()) Dark else Light,
        LocalType provides TypeScale,
        content = content,
    )
}

private val hueOverrides = mapOf(
    "en" to 218f, "de" to 352f, "id" to 6f, "ja" to 330f, "fr" to 232f, "es" to 24f,
    "it" to 150f, "nl" to 28f, "zh-Hans" to 0f, "zh-Hant" to 12f, "ko" to 200f, "pt" to 140f,
    "ru" to 260f, "ar" to 165f, "th" to 280f, "vi" to 120f, "tr" to 356f, "pl" to 340f,
)

fun languageColor(code: String, dark: Boolean): Color {
    val hue = hueOverrides[code] ?: ((code.fold(7) { acc, c -> acc * 31 + c.code } and 0x7fffffff) % 360).toFloat()
    return Color.hsl(hue, saturation = if (dark) 0.38f else 0.46f, lightness = if (dark) 0.34f else 0.31f)
}
