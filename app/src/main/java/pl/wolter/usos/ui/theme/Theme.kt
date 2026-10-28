package pl.wolter.usos.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import pl.wolter.usos.R
import pl.wolter.usos.data.ClassType

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Figtree = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.figtree, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

@Immutable
data class Pal(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val ink: Color,
    val text2: Color,
    val sub: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val chip: Color,
    val dash: Color,
    val hero: Color,
    val heroFg: Color,
    val primaryBtn: Color,
    val primaryBtnFg: Color,
    val navBg: Color,
    val blue: Color,
    val blueTint: Color,
    val nowRow: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
    val purple: Color,
    val scrim: Color,
    val todayCol: Color,
    val otherCol: Color,
    val toggleOff: Color,
) {
    fun type(t: ClassType): Pair<Color, Color> {
        val c = when (t) {
            ClassType.WYK -> blue
            ClassType.CW -> green
            ClassType.LAB -> purple
            ClassType.LEK -> orange
            ClassType.EGZ -> red
            ClassType.SEM -> Color(0xFF0E9AA7)
            ClassType.INNE -> sub
        }
        val tint = if (dark) c.copy(alpha = .20f).compositeOver(card) else when (t) {
            ClassType.WYK -> Color(0xFFEAF0FF)
            ClassType.CW -> Color(0xFFE6F6EC)
            ClassType.LAB -> Color(0xFFF0EAFD)
            ClassType.LEK -> Color(0xFFFCF0E1)
            ClassType.EGZ -> Color(0xFFFDEAEC)
            ClassType.SEM -> Color(0xFFE0F5F7)
            ClassType.INNE -> chip
        }
        return c to tint
    }

    /** [tło, kolor] oceny - jak gc() w projekcie. */
    fun grade(v: String): Pair<Color, Color> {
        val (fg, lightBg) = when (v) {
            "5", "4,5", "5,5", "ZAL", "zal", "TAK" -> green to Color(0xFFE6F6EC)
            "4", "3,5" -> blue to Color(0xFFEAF0FF)
            "3" -> orange to Color(0xFFFCF0E1)
            "2", "NZAL", "NK" -> red to Color(0xFFFDEAEC)
            else -> muted to chip
        }
        return (if (dark) fg.copy(alpha = .20f).compositeOver(card) else lightBg) to fg
    }

    fun kind(k: String): Color = when (k) { "red" -> red; "blue" -> blue; "orange" -> orange; "green" -> green; else -> text2 }
}

val LightPal = Pal(
    dark = false,
    bg = Color(0xFFF4F5F7), card = Color.White, ink = Color(0xFF111318), text2 = Color(0xFF4A4E58),
    sub = Color(0xFF6B6F7A), muted = Color(0xFF8A8E98), faint = Color(0xFFB8BCC4), line = Color(0xFFEEF0F3),
    chip = Color(0xFFF2F3F5), dash = Color(0xFFDADDE3), hero = Color(0xFF111318), heroFg = Color.White,
    primaryBtn = Color(0xFF111318), primaryBtnFg = Color.White, navBg = Color(0xF2FFFFFF),
    blue = Color(0xFF2F6BFF), blueTint = Color(0xFFEEF2FF), nowRow = Color(0xFFF2F5FF),
    red = Color(0xFFE04B5A), green = Color(0xFF1E9E57), orange = Color(0xFFD07A12), purple = Color(0xFF7C4DEB),
    scrim = Color(0x660E0F12), todayCol = Color(0xFFF2F5FF), otherCol = Color(0xFFF7F8FA), toggleOff = Color(0xFFD3D6DD),
)

val DarkPal = Pal(
    dark = true,
    bg = Color(0xFF0A0B0D), card = Color(0xFF17191E), ink = Color(0xFFF3F4F6), text2 = Color(0xFFC3C7CF),
    sub = Color(0xFF9A9EA8), muted = Color(0xFF7E828C), faint = Color(0xFF4F535C), line = Color(0xFF262930),
    chip = Color(0xFF22252C), dash = Color(0xFF33363E), hero = Color(0xFF1E2333), heroFg = Color.White,
    primaryBtn = Color(0xFFF3F4F6), primaryBtnFg = Color(0xFF111318), navBg = Color(0xF21C1E24),
    blue = Color(0xFF5B8CFF), blueTint = Color(0xFF1E2740), nowRow = Color(0xFF1C2233),
    red = Color(0xFFF0606E), green = Color(0xFF3CC177), orange = Color(0xFFE8963A), purple = Color(0xFF9C7AF2),
    scrim = Color(0x99000000), todayCol = Color(0xFF1B2130), otherCol = Color(0xFF1C1E23), toggleOff = Color(0xFF3A3D45),
)

val LocalPal = staticCompositionLocalOf { LightPal }

object P {
    val c: Pal @Composable get() = LocalPal.current
}

private fun ts(size: Int, w: Int, ls: Double = 0.0) = TextStyle(
    fontFamily = Figtree, fontSize = androidx.compose.ui.unit.TextUnit(size.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp),
    fontWeight = FontWeight(w),
    letterSpacing = androidx.compose.ui.unit.TextUnit((ls * size).toFloat(), androidx.compose.ui.unit.TextUnitType.Sp),
)

@Composable
fun UsosTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val target = if (dark) DarkPal else LightPal
    // płynne przejście kolorów przy zmianie motywu
    val bg by animateColorAsState(target.bg, tween(400), label = "bg")
    val card by animateColorAsState(target.card, tween(400), label = "card")
    val ink by animateColorAsState(target.ink, tween(400), label = "ink")
    val pal = target.copy(bg = bg, card = card, ink = ink)
    val scheme = if (dark) darkColorScheme(primary = pal.blue, background = pal.bg, surface = pal.card, onSurface = pal.ink)
    else lightColorScheme(primary = pal.blue, background = pal.bg, surface = pal.card, onSurface = pal.ink)
    val base = TextStyle(fontFamily = Figtree)
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            bodyLarge = base, bodyMedium = base, bodySmall = base, labelLarge = base, labelMedium = base,
            titleLarge = base, titleMedium = base, headlineSmall = base,
        ),
    ) {
        CompositionLocalProvider(LocalPal provides pal, content = content)
    }
}

object T {
    val hero = ts(34, 700, -.02)
    val heroSmall = ts(28, 700, -.02)
    val big = ts(42, 700, -.03)
    val num30 = ts(30, 800, -.02)
    val num34 = ts(34, 800, -.02)
    val h22 = ts(22, 700)
    val h24 = ts(24, 700)
    val h20 = ts(20, 700)
    val h17 = ts(17, 700)
    val t16 = ts(16, 600)
    val t15 = ts(15, 600)
    val t14 = ts(14, 500)
    val t14b = ts(14, 700)
    val t13 = ts(13, 400)
    val t13b = ts(13, 700)
    val t12 = ts(12, 400)
    val t12b = ts(12, 600)
    val cap = ts(12, 700, .06)
    val t11 = ts(11, 700)
    val t10 = ts(10, 600)
}
