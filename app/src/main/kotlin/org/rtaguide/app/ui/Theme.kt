package org.rtaguide.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00222E),
    secondary = Color(0xFF81C784),
    tertiary = Color(0xFFE040FB),
    background = Color(0xFF0F1419),
    surface = Color(0xFF151B22),
    surfaceVariant = Color(0xFF1F2730),
    error = Color(0xFFF44336),
)

@Composable
fun RtaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

val StatusOk = Color(0xFF4CAF50)
val StatusWarning = Color(0xFFFFC107)
val StatusOut = Color(0xFFF44336)
