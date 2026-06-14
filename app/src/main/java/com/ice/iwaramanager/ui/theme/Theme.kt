package com.ice.iwaramanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7D5E0),
    onPrimary = Color(0xFF171A21),
    secondary = Color(0xFF9FB4C7),
    background = Color(0xFF171A21),
    onBackground = Color(0xFFDDE6EE),
    surface = Color(0xFF1B2838),
    onSurface = Color(0xFFDDE6EE),
    surfaceVariant = Color(0xFF253445),
    onSurfaceVariant = Color(0xFFAFC0CE),
    outline = Color(0xFF3D4D5D)
)

@Composable
fun IwaraManagerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}