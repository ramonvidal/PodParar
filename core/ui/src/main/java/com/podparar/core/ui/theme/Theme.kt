package com.podparar.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.podparar.core.ui.R

/*
 * "Soft cards" (ChoreMe's look): a light grey page, plain white cards, black and grey text,
 * and one green used only for the main action and the selected tab. Show and episode artwork
 * is the only other colour. Red only means an error. Font: Figtree
 * (SIL Open Font License, bundled in res/font; license in assets/licenses).
 */

private val Green = Color(0xFF2E6B52)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EFE9),
    onPrimaryContainer = Color(0xFF173D2E),
    // Neutral on purpose: everything that isn't the main action stays black and grey.
    secondary = Color(0xFF4A4A47),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECE9),
    onSecondaryContainer = Color(0xFF1C1C1C),
    tertiary = Color(0xFF4A4A47),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECECE9),
    onTertiaryContainer = Color(0xFF1C1C1C),
    background = Color(0xFFF4F4F2),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFF4F4F2),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFECECE9),
    onSurfaceVariant = Color(0xFF636360),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFFBDBDB8),
    outlineVariant = Color(0xFFE2E2DF),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7A1A12),
    // Black for the one "add" button on a screen, apart from the green one.
    inverseSurface = Color(0xFF1C1C1C),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF8FCBAE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBAE),
    onPrimary = Color(0xFF0B3524),
    primaryContainer = Color(0xFF1F4A38),
    onPrimaryContainer = Color(0xFFCDEBDC),
    secondary = Color(0xFFC6C6C2),
    onSecondary = Color(0xFF2A2A28),
    secondaryContainer = Color(0xFF2E2E2C),
    onSecondaryContainer = Color(0xFFE6E6E2),
    tertiary = Color(0xFFC6C6C2),
    onTertiary = Color(0xFF2A2A28),
    tertiaryContainer = Color(0xFF2E2E2C),
    onTertiaryContainer = Color(0xFFE6E6E2),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E5),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE8E8E5),
    surfaceVariant = Color(0xFF2A2A28),
    onSurfaceVariant = Color(0xFFB0B0AB),
    surfaceContainerLowest = Color(0xFF1E1E1D),
    surfaceContainerLow = Color(0xFF1E1E1D),
    surfaceContainer = Color(0xFF1E1E1D),
    surfaceContainerHigh = Color(0xFF242423),
    surfaceContainerHighest = Color(0xFF2A2A28),
    outline = Color(0xFF6E6E6A),
    outlineVariant = Color(0xFF353533),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE8E8E5),
    inverseOnSurface = Color(0xFF1C1C1C),
    inversePrimary = Green,
)

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

private fun TextStyle.plain(weight: FontWeight? = null) = copy(fontFamily = Figtree, fontWeight = weight ?: fontWeight)

private val Base = Typography()

private val PlainTypography = Typography(
    displayLarge = Base.displayLarge.plain(FontWeight.Bold),
    displayMedium = Base.displayMedium.plain(FontWeight.Bold),
    displaySmall = Base.displaySmall.plain(FontWeight.Bold),
    headlineLarge = Base.headlineLarge.plain(FontWeight.Bold),
    headlineMedium = Base.headlineMedium.plain(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.plain(FontWeight.Bold),
    titleLarge = Base.titleLarge.plain(FontWeight.SemiBold),
    titleMedium = Base.titleMedium.plain(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.plain(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.plain(),
    bodyMedium = Base.bodyMedium.plain(),
    bodySmall = Base.bodySmall.plain(),
    labelLarge = Base.labelLarge.plain(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.plain(FontWeight.SemiBold),
    labelSmall = Base.labelSmall.plain(FontWeight.Medium),
)

private val PlainShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** The app has its own look, so the phone's wallpaper colours (dynamic color) are not used. */
@Composable
fun PodPararTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, typography = PlainTypography, shapes = PlainShapes, content = content)
}
