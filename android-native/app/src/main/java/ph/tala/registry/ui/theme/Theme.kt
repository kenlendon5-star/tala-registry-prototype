package ph.tala.registry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette lifted from the browser prototype (style.css) so the native shell reads as the same product.
// Dynamic colour is deliberately not used: the barangay sees one brand, not the device wallpaper.
private val Teal = Color(0xFF006B65)
private val TealSoft = Color(0xFFDCEEE9)
private val Ink = Color(0xFF183449)
private val Paper = Color(0xFFEDF3F7)
private val Line = Color(0xFFCBD8E1)
private val Muted = Color(0xFF536B7A)
private val Warn = Color(0xFF9C442E)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealSoft,
    onPrimaryContainer = Ink,
    secondary = Muted,
    onSecondary = Color.White,
    secondaryContainer = TealSoft,
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = TealSoft,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Warn,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD5CB),
    onPrimary = Color(0xFF00332F),
    primaryContainer = Color(0xFF004D48),
    onPrimaryContainer = Color(0xFFB9EFE7),
    secondary = Color(0xFFB3C4CE),
    onSecondary = Color(0xFF1D303B),
    background = Color(0xFF101B21),
    onBackground = Color(0xFFDCE4E9),
    surface = Color(0xFF16242B),
    onSurface = Color(0xFFDCE4E9),
    surfaceVariant = Color(0xFF25353D),
    onSurfaceVariant = Color(0xFFB3C4CE),
    outline = Color(0xFF4C6069),
    error = Color(0xFFFFB4A2),
    onError = Color(0xFF5F1600),
)

@Composable
fun TalaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
