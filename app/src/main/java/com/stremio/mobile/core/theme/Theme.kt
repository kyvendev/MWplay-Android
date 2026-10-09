package com.stremio.mobile.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.stremio.mobile.R

private val StremioDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB9A4FF),
    secondary = Color(0xFF8DB4FF),
    tertiary = Color(0xFFFFC66D),
    background = StremioBackground,
    surface = SurfaceMid,
    surfaceVariant = SurfaceHigh,
    surfaceContainer = SurfaceMid,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    surfaceContainerLow = SurfaceLow,
    surfaceContainerLowest = StremioBackground,
    onPrimary = Color(0xFF1B0F45),
    onSecondary = Color(0xFF071A3D),
    onTertiary = Color(0xFF2C1B00),
    onBackground = Color(0xFFECEDF3),
    onSurface = Color(0xFFECEDF3),
    onSurfaceVariant = MutedText,
    outline = Color(0x33FFFFFF),
    outlineVariant = HairlineBorder,
)

@OptIn(ExperimentalTextApi::class)
private fun createVariableFontFamily(resId: Int) = FontFamily(
    Font(resId = resId, weight = FontWeight(400), variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(resId = resId, weight = FontWeight(500), variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(resId = resId, weight = FontWeight(600), variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(resId = resId, weight = FontWeight(700), variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(resId = resId, weight = FontWeight(800), variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

enum class AppFont(val displayName: String, val resId: Int) {
    PLUS_JAKARTA_SANS("Plus Jakarta Sans", R.font.plus_jakarta_sans),
    INTER("Inter", R.font.inter),
    GEIST("Geist", R.font.geist),
    DM_SANS("DM Sans", R.font.dm_sans),
    FIGTREE("Figtree", R.font.figtree),
    ROBOTO_FLEX("Roboto Flex", R.font.roboto_flex);

    val fontFamily: FontFamily by lazy {
        createVariableFontFamily(resId)
    }
}

fun getTypography(fontFamily: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

@Composable
fun StremioMobileTheme(appFont: AppFont = AppFont.PLUS_JAKARTA_SANS, content: @Composable () -> Unit) {
    val typography = remember(appFont) { getTypography(appFont.fontFamily) }
    MaterialTheme(
        colorScheme = StremioDarkScheme,
        typography = typography,
        content = content,
    )
}

