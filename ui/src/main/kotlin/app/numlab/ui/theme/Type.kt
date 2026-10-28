package app.numlab.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.numlab.ui.R

// Bundled variable fonts (decision D5): Geist body, Geist Mono numerals/f(x), Bricolage display.
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

val Geist = variable(R.font.geist, 400, 500, 600, 700)
val GeistMono = variable(R.font.geist_mono, 400, 500, 600)
val Bricolage = variable(R.font.bricolage_grotesque, 500, 700, 800)

/** Tabular figures for every number on screen. */
const val TNUM = "tnum"

private val base = Typography()

val NumLabTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
    displayMedium = base.displayMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
    displaySmall = base.displaySmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Geist, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Geist, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Geist),
    bodyMedium = base.bodyMedium.copy(fontFamily = Geist),
    bodySmall = base.bodySmall.copy(fontFamily = Geist),
    labelLarge = base.labelLarge.copy(fontFamily = Geist, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Geist, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Geist, fontWeight = FontWeight.Medium),
)

/** Monospace numerals / f(x) field. */
val MonoStyle = TextStyle(fontFamily = GeistMono, fontFeatureSettings = TNUM)
