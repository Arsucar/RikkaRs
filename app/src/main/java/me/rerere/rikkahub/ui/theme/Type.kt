package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.UiTypographyFamily
import me.rerere.rikkahub.data.datastore.UiTypographyWeightBias
import me.rerere.rikkahub.data.datastore.coerceUiTypographyLetterSpacingScale
import me.rerere.rikkahub.data.datastore.coerceUiTypographyLineHeightScale
import me.rerere.rikkahub.data.datastore.coerceUiTypographyScale

/**
 * Brand / system typography for [RikkahubTheme].
 *
 * Default path uses system/M3 fonts with explicit weight / lineHeight / letterSpacing for
 * clearer hierarchy (especially CJK). Brand path optionally applies Google Sans Flex.
 *
 * Chat message body still follows [LocalChatFontFamily] / [ChatFontProvider] and is not forced
 * onto this family — chat font switching remains independent of the global type scale.
 */
private val base = Typography()

private data class TypeMetrics(
    val fontWeight: FontWeight,
    val lineHeight: TextUnit,
    val letterSpacing: TextUnit,
    val brandFontFamily: FontFamily? = null,
)

private fun brandMetrics(): Map<String, TypeMetrics> = mapOf(
    // Display
    "displayLarge" to TypeMetrics(FontWeight.Normal, 64.sp, (-0.25).sp, GoogleSansFlex.Display.Normal.Large),
    "displayMedium" to TypeMetrics(FontWeight.Normal, 52.sp, 0.sp, GoogleSansFlex.Display.Normal.Medium),
    "displaySmall" to TypeMetrics(FontWeight.Normal, 44.sp, 0.sp, GoogleSansFlex.Display.Normal.Small),
    "displayLargeEmphasized" to TypeMetrics(FontWeight.Bold, 64.sp, (-0.25).sp, GoogleSansFlex.Display.Emphasized.Large),
    "displayMediumEmphasized" to TypeMetrics(FontWeight.Bold, 52.sp, 0.sp, GoogleSansFlex.Display.Emphasized.Medium),
    "displaySmallEmphasized" to TypeMetrics(FontWeight.Bold, 44.sp, 0.sp, GoogleSansFlex.Display.Emphasized.Small),
    // Headline
    "headlineLarge" to TypeMetrics(FontWeight.Normal, 40.sp, 0.sp, GoogleSansFlex.Headline.Normal.Large),
    "headlineMedium" to TypeMetrics(FontWeight.Normal, 36.sp, 0.sp, GoogleSansFlex.Headline.Normal.Medium),
    "headlineSmall" to TypeMetrics(FontWeight.Normal, 32.sp, 0.sp, GoogleSansFlex.Headline.Normal.Small),
    "headlineLargeEmphasized" to TypeMetrics(FontWeight.Bold, 40.sp, 0.sp, GoogleSansFlex.Headline.Emphasized.Large),
    "headlineMediumEmphasized" to TypeMetrics(FontWeight.Bold, 36.sp, 0.sp, GoogleSansFlex.Headline.Emphasized.Medium),
    "headlineSmallEmphasized" to TypeMetrics(FontWeight.Bold, 32.sp, 0.sp, GoogleSansFlex.Headline.Emphasized.Small),
    // Title — slightly heavier than M3 defaults for clearer page/section contrast
    "titleLarge" to TypeMetrics(FontWeight.Medium, 28.sp, 0.sp, GoogleSansFlex.Title.Normal.Large),
    "titleMedium" to TypeMetrics(FontWeight.Medium, 24.sp, 0.15.sp, GoogleSansFlex.Title.Normal.Medium),
    "titleSmall" to TypeMetrics(FontWeight.Medium, 20.sp, 0.1.sp, GoogleSansFlex.Title.Normal.Small),
    "titleLargeEmphasized" to TypeMetrics(FontWeight.Bold, 28.sp, 0.sp, GoogleSansFlex.Title.Emphasized.Large),
    "titleMediumEmphasized" to TypeMetrics(FontWeight.Bold, 24.sp, 0.15.sp, GoogleSansFlex.Title.Emphasized.Medium),
    "titleSmallEmphasized" to TypeMetrics(FontWeight.Bold, 20.sp, 0.1.sp, GoogleSansFlex.Title.Emphasized.Small),
    // Body
    "bodyLarge" to TypeMetrics(FontWeight.Normal, 24.sp, 0.15.sp, GoogleSansFlex.Body.Normal.Large),
    "bodyMedium" to TypeMetrics(FontWeight.Normal, 20.sp, 0.25.sp, GoogleSansFlex.Body.Normal.Medium),
    "bodySmall" to TypeMetrics(FontWeight.Normal, 16.sp, 0.4.sp, GoogleSansFlex.Body.Normal.Small),
    "bodyLargeEmphasized" to TypeMetrics(FontWeight.SemiBold, 24.sp, 0.15.sp, GoogleSansFlex.Body.Emphasized.Large),
    "bodyMediumEmphasized" to TypeMetrics(FontWeight.SemiBold, 20.sp, 0.25.sp, GoogleSansFlex.Body.Emphasized.Medium),
    "bodySmallEmphasized" to TypeMetrics(FontWeight.SemiBold, 16.sp, 0.4.sp, GoogleSansFlex.Body.Emphasized.Small),
    // Label
    "labelLarge" to TypeMetrics(FontWeight.Medium, 20.sp, 0.1.sp, GoogleSansFlex.Label.Normal.Large),
    "labelMedium" to TypeMetrics(FontWeight.Medium, 16.sp, 0.5.sp, GoogleSansFlex.Label.Normal.Medium),
    "labelSmall" to TypeMetrics(FontWeight.Medium, 16.sp, 0.5.sp, GoogleSansFlex.Label.Normal.Small),
    "labelLargeEmphasized" to TypeMetrics(FontWeight.SemiBold, 20.sp, 0.1.sp, GoogleSansFlex.Label.Emphasized.Large),
    "labelMediumEmphasized" to TypeMetrics(FontWeight.SemiBold, 16.sp, 0.5.sp, GoogleSansFlex.Label.Emphasized.Medium),
    "labelSmallEmphasized" to TypeMetrics(FontWeight.SemiBold, 16.sp, 0.5.sp, GoogleSansFlex.Label.Emphasized.Small),
)

fun resolveAppTypography(
    family: UiTypographyFamily = UiTypographyFamily.SYSTEM,
    weightBias: UiTypographyWeightBias = UiTypographyWeightBias.DEFAULT,
    scale: Float = 1.0f,
    lineHeightScale: Float = 1.0f,
    letterSpacingScale: Float = 1.0f,
): Typography {
    val sizeScale = scale.coerceUiTypographyScale()
    val lhScale = lineHeightScale.coerceUiTypographyLineHeightScale()
    val lsScale = letterSpacingScale.coerceUiTypographyLetterSpacingScale()
    val metrics = brandMetrics()
    val useBrand = family == UiTypographyFamily.BRAND

    fun style(key: String, baseStyle: TextStyle): TextStyle {
        val m = metrics.getValue(key)
        val weight = applyWeightBias(m.fontWeight, weightBias)
        val fontSize = baseStyle.fontSize.scaleSp(sizeScale)
        val lineHeight = m.lineHeight.scaleSp(sizeScale * lhScale)
        val letterSpacing = m.letterSpacing.scaleSp(lsScale)
        return if (useBrand && m.brandFontFamily != null) {
            baseStyle.copy(
                fontFamily = m.brandFontFamily,
                fontWeight = weight,
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
            )
        } else {
            baseStyle.copy(
                fontWeight = weight,
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
            )
        }
    }

    return Typography(
        displayLarge = style("displayLarge", base.displayLarge),
        displayMedium = style("displayMedium", base.displayMedium),
        displaySmall = style("displaySmall", base.displaySmall),
        displayLargeEmphasized = style("displayLargeEmphasized", base.displayLargeEmphasized),
        displayMediumEmphasized = style("displayMediumEmphasized", base.displayMediumEmphasized),
        displaySmallEmphasized = style("displaySmallEmphasized", base.displaySmallEmphasized),
        headlineLarge = style("headlineLarge", base.headlineLarge),
        headlineMedium = style("headlineMedium", base.headlineMedium),
        headlineSmall = style("headlineSmall", base.headlineSmall),
        headlineLargeEmphasized = style("headlineLargeEmphasized", base.headlineLargeEmphasized),
        headlineMediumEmphasized = style("headlineMediumEmphasized", base.headlineMediumEmphasized),
        headlineSmallEmphasized = style("headlineSmallEmphasized", base.headlineSmallEmphasized),
        titleLarge = style("titleLarge", base.titleLarge),
        titleMedium = style("titleMedium", base.titleMedium),
        titleSmall = style("titleSmall", base.titleSmall),
        titleLargeEmphasized = style("titleLargeEmphasized", base.titleLargeEmphasized),
        titleMediumEmphasized = style("titleMediumEmphasized", base.titleMediumEmphasized),
        titleSmallEmphasized = style("titleSmallEmphasized", base.titleSmallEmphasized),
        bodyLarge = style("bodyLarge", base.bodyLarge),
        bodyMedium = style("bodyMedium", base.bodyMedium),
        bodySmall = style("bodySmall", base.bodySmall),
        bodyLargeEmphasized = style("bodyLargeEmphasized", base.bodyLargeEmphasized),
        bodyMediumEmphasized = style("bodyMediumEmphasized", base.bodyMediumEmphasized),
        bodySmallEmphasized = style("bodySmallEmphasized", base.bodySmallEmphasized),
        labelLarge = style("labelLarge", base.labelLarge),
        labelMedium = style("labelMedium", base.labelMedium),
        labelSmall = style("labelSmall", base.labelSmall),
        labelLargeEmphasized = style("labelLargeEmphasized", base.labelLargeEmphasized),
        labelMediumEmphasized = style("labelMediumEmphasized", base.labelMediumEmphasized),
        labelSmallEmphasized = style("labelSmallEmphasized", base.labelSmallEmphasized),
    )
}

/** Default SYSTEM typography for any leftover static references. */
val Typography = resolveAppTypography()

private fun applyWeightBias(weight: FontWeight, bias: UiTypographyWeightBias): FontWeight {
    return when (bias) {
        UiTypographyWeightBias.DEFAULT -> weight
        UiTypographyWeightBias.LIGHT -> when {
            weight >= FontWeight.Bold -> FontWeight.SemiBold
            weight >= FontWeight.SemiBold -> FontWeight.Medium
            weight >= FontWeight.Medium -> FontWeight.Normal
            else -> weight
        }
        UiTypographyWeightBias.MEDIUM -> when {
            weight >= FontWeight.Bold -> FontWeight.ExtraBold
            weight >= FontWeight.SemiBold -> FontWeight.Bold
            weight >= FontWeight.Medium -> FontWeight.SemiBold
            weight >= FontWeight.Normal -> FontWeight.Medium
            else -> weight
        }
        UiTypographyWeightBias.BOLD -> when {
            weight >= FontWeight.Bold -> FontWeight.Black
            weight >= FontWeight.SemiBold -> FontWeight.ExtraBold
            weight >= FontWeight.Medium -> FontWeight.Bold
            weight >= FontWeight.Normal -> FontWeight.SemiBold
            else -> FontWeight.Medium
        }
    }
}

private fun TextUnit.scaleSp(factor: Float): TextUnit {
    if (type != TextUnitType.Sp || factor == 1f) return this
    return (value * factor).sp
}

@OptIn(ExperimentalTextApi::class)
val JetbrainsMono = FontFamily(
    Font(
        resId = R.font.jetbrains_mono,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight),
        )
    )
)
