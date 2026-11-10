package com.latifi.wallet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.latifi.wallet.R

/**
 * Report 6.1.1 typography: 6 sizes (32, 24, 18, 16, 14, 12 sp) and 4 weights (400, 500,
 * 600, 700). Line height is 1.5 times the size for Body L, Body M, Label and Caption.
 * Both families come from variable fonts, so each weight is a FontVariation setting on
 * the same file (research F7). Variable fonts need API 26, which is the app minimum.
 *
 * The families and their helper live in an object of their own. A top-level function or val
 * in this file would run the file's static initializer, which builds LatiFiTypography from
 * LatiFiType, and that closes an initialization cycle that leaves the slots null.
 */
object LatiFiFonts {
    private fun variable(resId: Int, weight: Int): Font =
        Font(
            resId = resId,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )

    val Sora = FontFamily(
        variable(R.font.sora, 600),
        variable(R.font.sora, 700),
    )

    val Inter = FontFamily(
        variable(R.font.inter, 400),
        variable(R.font.inter, 500),
        variable(R.font.inter, 600),
    )
}

private const val TABULAR = "tnum"

object LatiFiType {
    val Display = TextStyle(
        fontFamily = LatiFiFonts.Sora,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    )
    val Headline = TextStyle(
        fontFamily = LatiFiFonts.Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    )
    val Title = TextStyle(
        fontFamily = LatiFiFonts.Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )
    val BodyL = TextStyle(
        fontFamily = LatiFiFonts.Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val BodyM = TextStyle(
        fontFamily = LatiFiFonts.Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )
    val Label = TextStyle(
        fontFamily = LatiFiFonts.Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )
    val Caption = TextStyle(
        fontFamily = LatiFiFonts.Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )

    /** The seven roles in the order of the UI-SPEC Typography table. */
    val roles: List<TextStyle> = listOf(Display, Headline, Title, BodyL, BodyM, Label, Caption)

    /** Amounts and installments use tabular figures so columns align. */
    object Amount {
        val Display = LatiFiType.Display.copy(fontFeatureSettings = TABULAR)
        val Headline = LatiFiType.Headline.copy(fontFeatureSettings = TABULAR)
        val Title = LatiFiType.Title.copy(fontFeatureSettings = TABULAR)
        val BodyL = LatiFiType.BodyL.copy(fontFeatureSettings = TABULAR)
        val BodyM = LatiFiType.BodyM.copy(fontFeatureSettings = TABULAR)

        val all: List<TextStyle> = listOf(Display, Headline, Title, BodyL, BodyM)
    }
}

/**
 * Every Material 3 slot maps onto one of the seven roles, so no component can pull in a
 * default Material size and break the six-size rule.
 */
val LatiFiTypography = Typography(
    displayLarge = LatiFiType.Display,
    displayMedium = LatiFiType.Display,
    displaySmall = LatiFiType.Display,
    headlineLarge = LatiFiType.Headline,
    headlineMedium = LatiFiType.Headline,
    headlineSmall = LatiFiType.Headline,
    titleLarge = LatiFiType.Headline,
    titleMedium = LatiFiType.Title,
    titleSmall = LatiFiType.Label,
    bodyLarge = LatiFiType.BodyL,
    bodyMedium = LatiFiType.BodyM,
    bodySmall = LatiFiType.Caption,
    labelLarge = LatiFiType.Label,
    labelMedium = LatiFiType.Label,
    labelSmall = LatiFiType.Caption,
)
