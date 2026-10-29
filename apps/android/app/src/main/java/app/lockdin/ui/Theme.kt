package app.lockdin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.lockdin.R
import app.lockdin.state.ThemeChoice

val Outfit = FontFamily(
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
    Font(R.font.outfit_700, FontWeight.Bold),
)
val Figtree = FontFamily(
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_600, FontWeight.SemiBold),
)

/** Lockd In's own palette, available everywhere (Material colors are mapped from it). */
val LocalColors = staticCompositionLocalOf { LightColors }

object Type {
    val display = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp)
    val timer = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp, fontFeatureSettings = "tnum")
    val title = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val fact = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, fontFeatureSettings = "tnum")
    val body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val button = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
}

@Composable
fun isDark(choice: ThemeChoice): Boolean = when (choice) {
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

@Composable
fun LockdInTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.ink, onPrimary = c.onInk, background = c.ground, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.taupeSoft, onSurfaceVariant = c.inkMuted,
            surfaceContainer = c.taupeSoft, secondaryContainer = c.taupe, onSecondaryContainer = c.ink,
            outline = c.controlLine, outlineVariant = c.line, error = c.danger, errorContainer = c.dangerSoft,
        )
    } else {
        lightColorScheme(
            primary = c.ink, onPrimary = c.onInk, background = c.ground, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.taupeSoft, onSurfaceVariant = c.inkMuted,
            surfaceContainer = c.taupeSoft, secondaryContainer = c.taupe, onSecondaryContainer = c.ink,
            outline = c.controlLine, outlineVariant = c.line, error = c.danger, errorContainer = c.dangerSoft,
        )
    }
    val typography = Typography(
        bodyLarge = Type.body, bodyMedium = Type.caption, labelLarge = Type.label,
        titleLarge = Type.title, labelMedium = Type.caption,
    )
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
