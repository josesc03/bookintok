package com.bookintok.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bookintok.app.R

/**
 * Bookintok palette: the Hukly palette with every hue rotated by +113°, the turn that takes Hook
 * Orange to green. Saturation and lightness are kept, so warm tones become green and the cool
 * (navy/blue) tones become burgundy/pink. Names keep the Hukly role they come from.
 */
object BookintokColors {
    val Green = Color(0xFF52D974) // Hook Orange
    val GreenAccent = Color(0xFF42B65F) // Warm Accent
    val GreenSurface = Color(0xFFD6EFDA) // Warm Surface
    val GreenSurfaceDark = Color(0xFFCDE6D1) // Warm Surface Dark
    val GreenSurfaceDarker = Color(0xFFC4DBC8) // Warm Surface Darker
    val GreenWhite = Color(0xFFE9F4EB) // Warm White
    val GreenGray = Color(0xFF5C665D) // Warm Gray
    val Ink = Color(0xFF45202F) // Ink Navy
    val InkGray = Color(0xFF917382) // Cool Gray
    val Pink = Color(0xFFB55884) // Cool Accent

    /** Darker green for small accents on light backgrounds, where the brand green is too light to read. */
    val GreenDeep = Color(0xFF1E6B35)

    /** Green for small text and icons: the deep green on light backgrounds, the brand green on dark ones. */
    val Accent: Color
        @Composable @ReadOnlyComposable
        get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Green else GreenDeep

    // Status colors are not brand colors: they stay as in Hukly
    val ErrorFg = Color(0xFFB42318)
    val ErrorBg = Color(0xFFFEE4E2)
}

private val Light = with(BookintokColors) {
    lightColorScheme(
        primary = Green,
        // Hukly puts light text on its orange; on this green light text is hard to read, so the ink is used
        onPrimary = Ink,
        primaryContainer = GreenSurface,
        onPrimaryContainer = Ink,
        inversePrimary = Green,
        secondary = GreenAccent,
        onSecondary = Ink,
        secondaryContainer = GreenSurfaceDark,
        onSecondaryContainer = Ink,
        tertiary = Pink,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF6DDE8),
        onTertiaryContainer = Ink,
        background = GreenWhite,
        onBackground = Ink,
        surface = GreenWhite,
        onSurface = Ink,
        surfaceVariant = GreenSurface,
        onSurfaceVariant = GreenGray,
        surfaceTint = Green,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F8F2),
        surfaceContainer = Color(0xFFE2F1E5),
        surfaceContainerHigh = GreenSurface,
        surfaceContainerHighest = GreenSurfaceDark,
        inverseSurface = Ink,
        inverseOnSurface = GreenWhite,
        outline = InkGray,
        outlineVariant = GreenSurfaceDarker,
        error = ErrorFg,
        onError = Color.White,
        errorContainer = ErrorBg,
        onErrorContainer = ErrorFg,
    )
}

/** Hukly has no dark theme; this one is built from the same palette over a deep ink background. */
private val Dark = with(BookintokColors) {
    darkColorScheme(
        primary = Green,
        onPrimary = Color(0xFF1F1016),
        primaryContainer = Color(0xFF1F5A2F),
        onPrimaryContainer = GreenSurface,
        inversePrimary = GreenAccent,
        secondary = Color(0xFF8FD9A1),
        onSecondary = Color(0xFF1F1016),
        secondaryContainer = Color(0xFF2F4A35),
        onSecondaryContainer = GreenSurface,
        tertiary = Color(0xFFE08AB2),
        onTertiary = Color(0xFF1F1016),
        background = Color(0xFF1F1016),
        onBackground = GreenWhite,
        surface = Color(0xFF1F1016),
        onSurface = GreenWhite,
        surfaceVariant = Color(0xFF3A2430),
        onSurfaceVariant = GreenSurfaceDarker,
        surfaceContainerLowest = Color(0xFF190C11),
        surfaceContainerLow = Color(0xFF26141C),
        surfaceContainer = Color(0xFF2D1922),
        surfaceContainerHigh = Color(0xFF341E28),
        surfaceContainerHighest = Color(0xFF3B232E),
        inverseSurface = GreenWhite,
        inverseOnSurface = Ink,
        outline = InkGray,
        outlineVariant = Color(0xFF4F3541),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = ErrorBg,
    )
}

// Typography and shapes follow the Hukly design system: Space Grotesk for display text, Inter for
// everything else, a 4 px spacing scale and 8/12 px corner radii.

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val SpaceGrotesk = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
        .map { variableFont(R.font.space_grotesk, it) },
)

private val Inter = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
        .map { variableFont(R.font.inter, it) },
)

private fun display(size: Int, weight: FontWeight, lineHeight: Float = 1.1f) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = (-0.01).em,
)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Float = 1.5f, tight: Boolean = false) =
    TextStyle(
        fontFamily = Inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        letterSpacing = if (tight) (-0.01).em else 0.em,
    )

/** Hukly text styles mapped onto Material roles (display-lg 64 → h4 22 → body 16 → caption 12). */
val BookintokTypography = Typography(
    displayLarge = display(48, FontWeight.Bold),
    displayMedium = display(40, FontWeight.Medium),
    displaySmall = display(36, FontWeight.SemiBold),
    headlineLarge = display(32, FontWeight.SemiBold),
    headlineMedium = display(28, FontWeight.SemiBold),
    headlineSmall = display(22, FontWeight.Medium),
    titleLarge = display(22, FontWeight.Medium, lineHeight = 1.3f),
    titleMedium = body(16, FontWeight.SemiBold, lineHeight = 1.4f),
    titleSmall = body(14, FontWeight.SemiBold, lineHeight = 1.4f),
    bodyLarge = body(18),
    bodyMedium = body(16),
    bodySmall = body(14, lineHeight = 1.4f, tight = true),
    labelLarge = body(14, FontWeight.SemiBold, lineHeight = 1.2f),
    labelMedium = body(12, FontWeight.Medium, lineHeight = 1.3f, tight = true),
    labelSmall = body(12, FontWeight.Bold, lineHeight = 1.3f, tight = true),
)

val BookintokShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Brand colors always win over the wallpaper-based dynamic colors of Android 12+. */
@Composable
fun BookintokTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = BookintokTypography,
        shapes = BookintokShapes,
        content = content,
    )
}
