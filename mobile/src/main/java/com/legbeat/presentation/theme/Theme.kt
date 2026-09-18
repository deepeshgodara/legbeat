package com.legbeat.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AsphaltBlack = Color(0xFF121212)
val CharcoalSurface = Color(0xFF1E1E1E)
val CardSurface = Color(0xFF282828)
val ElectricYellow = Color(0xFFFFD700)
val ElectricMint = Color(0xFF00E676)
val DangerRed = Color(0xFFFF5252)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricYellow,
    onPrimary = AsphaltBlack,
    secondary = ElectricMint,
    onSecondary = AsphaltBlack,
    background = AsphaltBlack,
    onBackground = Color.White,
    surface = CharcoalSurface,
    onSurface = Color.White,
    surfaceVariant = CardSurface,
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = DangerRed,
    onError = Color.White
)

@Composable
fun LegBeatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
