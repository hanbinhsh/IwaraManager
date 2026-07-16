package com.ice.iwaramanager.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD6C1),
    onPrimary = Color(0xFF08251E),
    primaryContainer = Color(0xFF16493D),
    onPrimaryContainer = Color(0xFFC8F2E6),
    secondary = Color(0xFFD7B983),
    onSecondary = Color(0xFF2B1D05),
    secondaryContainer = Color(0xFF4C3714),
    onSecondaryContainer = Color(0xFFF7DEAA),
    tertiary = Color(0xFFE9A5A5),
    onTertiary = Color(0xFF3A1012),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF30353C),
    onSurfaceVariant = Color(0xFFC3CBD3),
    surfaceContainer = Color(0xFF202429),
    surfaceContainerHigh = Color(0xFF282D34),
    surfaceContainerHighest = Color(0xFF313841),
    outline = Color(0xFF707A84),
    outlineVariant = Color(0xFF424952),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun IwaraManagerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = DarkColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
