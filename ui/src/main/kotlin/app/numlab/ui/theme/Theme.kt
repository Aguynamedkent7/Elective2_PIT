package app.numlab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand colours Material 3 has no slot for (`--c-*` / `--mc-*` in template3.html, `.cD` scope). */
@Immutable
data class NumLabColors(
    val curve: Color,
    val hot: Color,
    val good: Color,
    val goodBg: Color,
    val warn: Color,
    val warnBg: Color,
    val bad: Color,
    val badBg: Color,
    val glass: Color,
    val bisectionBg: Color, val bisectionInk: Color,
    val newtonBg: Color, val newtonInk: Color,
    val secantBg: Color, val secantInk: Color,
    val lagrangeBg: Color, val lagrangeInk: Color,
    val newtonDdBg: Color, val newtonDdInk: Color,
)

val LogoTile = Color(0xFF2F5BEA)
val LogoDot = Color(0xFFFF6A1F)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F5BEA), onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F5F8), onBackground = Color(0xFF0F1626),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0F1626),
    surfaceVariant = Color(0xFFEEF1F6), onSurfaceVariant = Color(0xFF667085),
    surfaceContainer = Color(0xFFEEF1F6), surfaceContainerHigh = Color(0xFFEEF1F6),
    outline = Color(0xFFDDE2EA), outlineVariant = Color(0xFFDDE2EA),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFDE3E0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7C9BFF), onPrimary = Color(0xFF0B0F18),
    background = Color(0xFF0B0F18), onBackground = Color(0xFFECEFF6),
    surface = Color(0xFF151B28), onSurface = Color(0xFFECEFF6),
    surfaceVariant = Color(0xFF1D2535), onSurfaceVariant = Color(0xFF9AA4B8),
    surfaceContainer = Color(0xFF1D2535), surfaceContainerHigh = Color(0xFF1D2535),
    outline = Color(0xFF2A3346), outlineVariant = Color(0xFF2A3346),
    error = Color(0xFFFF8F86), errorContainer = Color(0xFF3D1714),
)

private val LightExtra = NumLabColors(
    curve = Color(0xFF2F5BEA), hot = Color(0xFFE5560F),
    good = Color(0xFF0B7A4B), goodBg = Color(0xFFDDF5E8), warn = Color(0xFF8A5200), warnBg = Color(0xFFFFEFD0),
    bad = Color(0xFFB3261E), badBg = Color(0xFFFDE3E0), glass = Color(0xCCFFFFFF),
    bisectionBg = Color(0xFFE2E8FF), bisectionInk = Color(0xFF1C2554),
    newtonBg = Color(0xFFD8F3E4), newtonInk = Color(0xFF11382A),
    secantBg = Color(0xFFFFE5D3), secantInk = Color(0xFF4D2511),
    lagrangeBg = Color(0xFFEADDFB), lagrangeInk = Color(0xFF2E1A52),
    newtonDdBg = Color(0xFFD5EDF9), newtonDdInk = Color(0xFF0D3550),
)

private val DarkExtra = NumLabColors(
    curve = Color(0xFF7C9BFF), hot = Color(0xFFFF8A4C),
    good = Color(0xFF5FD79B), goodBg = Color(0xFF12392A), warn = Color(0xFFFFC66B), warnBg = Color(0xFF3A2A0C),
    bad = Color(0xFFFF8F86), badBg = Color(0xFF3D1714), glass = Color(0xCC151B28),
    bisectionBg = Color(0xFF1F2652), bisectionInk = Color(0xFFD5DCFF),
    newtonBg = Color(0xFF12392A), newtonInk = Color(0xFFC9F0DA),
    secantBg = Color(0xFF4A2512), secantInk = Color(0xFFFFD9C2),
    lagrangeBg = Color(0xFF34205A), lagrangeInk = Color(0xFFE6D6FF),
    newtonDdBg = Color(0xFF0F3A55), newtonDdInk = Color(0xFFCBEBFB),
)

private val LocalNumLabColors = staticCompositionLocalOf { LightExtra }

object NumLabTheme {
    val colors: NumLabColors
        @Composable @ReadOnlyComposable get() = LocalNumLabColors.current
}

@Composable
fun NumLabTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalNumLabColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = scheme, typography = NumLabTypography, content = content)
    }
}
