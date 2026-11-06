package com.emduah.personal.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.emduah.personal.R

private val Lato = FontFamily(Font(R.font.lato_regular), Font(R.font.lato_bold, FontWeight.Bold))
private val Light = lightColorScheme(
    primary = Color(0xFF18181B), onPrimary = Color.White,
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF18181B),
    surfaceTint = Color(0xFF18181B),
    surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainerHigh = Color(0xFFF4F4F5), surfaceContainerHighest = Color(0xFFE4E4E7),
    secondary = Color(0xFF52525B), onSecondary = Color.White,
    surface = Color.White, onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF4F4F5), onSurfaceVariant = Color(0xFF62626B),
    secondaryContainer = Color(0xFFE4E4E7), onSecondaryContainer = Color(0xFF18181B),
    outline = Color(0xFF777780), outlineVariant = Color(0xFFE4E4E7)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFAFAFA), onPrimary = Color(0xFF18181B),
    background = Color(0xFF09090B), onBackground = Color(0xFFFAFAFA),
    surfaceTint = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFF18181B), surfaceContainerLow = Color(0xFF121214),
    surfaceContainerHigh = Color(0xFF202023), surfaceContainerHighest = Color(0xFF27272A),
    secondary = Color(0xFFD4D4D8), onSecondary = Color(0xFF18181B),
    surface = Color(0xFF121214), onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF27272A), onSurfaceVariant = Color(0xFFB0B0B8),
    secondaryContainer = Color(0xFF303034), onSecondaryContainer = Color(0xFFFAFAFA),
    outline = Color(0xFF85858F), outlineVariant = Color(0xFF303034)
)
// Read inside composition so every screen follows system appearance changes.
internal val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val Panel: Color @Composable get() = MaterialTheme.colorScheme.surface
internal val BrandPaper: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF151515) else Color(0xFFF3F1EC)

@Composable internal fun EmduahTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            displayLarge = base.displayLarge.copy(fontFamily = Lato),
            displayMedium = base.displayMedium.copy(fontFamily = Lato),
            displaySmall = base.displaySmall.copy(fontFamily = Lato),
            headlineLarge = base.headlineLarge.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
            titleSmall = base.titleSmall.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
            bodyLarge = base.bodyLarge.copy(fontFamily = Lato),
            bodyMedium = base.bodyMedium.copy(fontFamily = Lato),
            bodySmall = base.bodySmall.copy(fontFamily = Lato),
            labelLarge = base.labelLarge.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
            labelMedium = base.labelMedium.copy(fontFamily = Lato),
            labelSmall = base.labelSmall.copy(fontFamily = Lato)
        ), content = content
    )
}
