package it.luma.tapo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import LumaDesktop.generated.resources.Res
import org.jetbrains.compose.resources.Font

private val LumaLavender = Color(0xFF7C66D5)
private val LumaLavenderDark = Color(0xFFB7A9F5)

val LumaLightScheme = lightColorScheme(
    primary = LumaLavender,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FB),
    onPrimaryContainer = Color(0xFF22194D),
    secondary = Color(0xFF6A55B5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E0F9),
    onSecondaryContainer = Color(0xFF221A44),
    tertiary = Color(0xFFB5678A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCDDE7),
    onTertiaryContainer = Color(0xFF431C2C),
    background = Color(0xFFF7F5FC),
    onBackground = Color(0xFF1C1B23),
    surface = Color(0xFFF7F5FC),
    onSurface = Color(0xFF1C1B23),
    surfaceVariant = Color(0xFFE7E1F1),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A7582),
    outlineVariant = Color(0xFFE7E1F1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = LumaLavender
)

val LumaDarkScheme = darkColorScheme(
    primary = LumaLavenderDark,
    onPrimary = Color(0xFF382A6E),
    primaryContainer = Color(0xFF543B9C),
    onPrimaryContainer = Color(0xFFE9E3FB),
    secondary = Color(0xFFD6BFEC),
    onSecondary = Color(0xFF392B62),
    secondaryContainer = Color(0xFF4F3C80),
    onSecondaryContainer = Color(0xFFE8E0F9),
    tertiary = Color(0xFFE5A0BC),
    onTertiary = Color(0xFF63182F),
    tertiaryContainer = Color(0xFF873952),
    onTertiaryContainer = Color(0xFFFCDDE7),
    background = Color(0xFF14131A),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF14131A),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFC9C4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = LumaLavenderDark
)

@Composable
fun LumaTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val sans = FontFamily(Font(Res.font.dm_sans, FontWeight.Normal))
    val serif = FontFamily(Font(Res.font.dm_serif_display, FontWeight.Normal))

    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = serif),
        displayMedium = base.displayMedium.copy(fontFamily = serif),
        headlineLarge = base.headlineLarge.copy(fontFamily = serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = serif),
        titleLarge = base.titleLarge.copy(fontFamily = serif),
        titleMedium = base.titleMedium.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = sans),
        bodySmall = base.bodySmall.copy(fontFamily = sans),
        labelLarge = base.labelLarge.copy(fontFamily = sans, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = sans, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = sans)
    )

    MaterialTheme(
        colorScheme = if (darkTheme) LumaDarkScheme else LumaLightScheme,
        typography = typography,
        content = content
    )
}
