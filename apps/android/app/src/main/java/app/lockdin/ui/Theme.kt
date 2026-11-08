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

// Type (R53, owner 2026-11-07: no generic fonts): Archivo by Omnibus-Type (OFL, design/fonts/), a
// 19th-century American grotesque with a width axis. Expanded cuts are the "stamped" voice, like
// lettering struck into a padlock body: titles, timer digits, big numbers, primary buttons. Normal
// width runs the interface. Width, not colour, carries hierarchy. Static instances made from the
// variable font with fontTools (wdth 100 / 112.5 / 125).
val Archivo = FontFamily(
    Font(R.font.archivo_400, FontWeight.Normal),
    Font(R.font.archivo_500, FontWeight.Medium),
    Font(R.font.archivo_600, FontWeight.SemiBold),
)
val ArchivoWide = FontFamily(
    Font(R.font.archivo_wide_600, FontWeight.SemiBold),
    Font(R.font.archivo_wide_800, FontWeight.ExtraBold),
)
/** Semi-expanded heavy digits: wide enough to feel stamped, narrow enough for the dial face. */
val ArchivoDigits = FontFamily(Font(R.font.archivo_semiwide_800, FontWeight.ExtraBold))
/**
 * Bricolage Grotesque (Mathieu Triay, OFL; owner 2026-11-07: Freedom-like character): the voice for
 * greetings, headings, buttons and clock digits. Static cuts at display optical size (48) for headings,
 * text size (16) for buttons, and slightly narrowed (wdth 92) for digits so they sit inside the dial.
 */
val Bricolage = FontFamily(
    Font(R.font.bricolage_text_600, FontWeight.Medium),
    Font(R.font.bricolage_display_600, FontWeight.SemiBold),
    Font(R.font.bricolage_display_700, FontWeight.Bold),
)
val BricolageDigits = FontFamily(Font(R.font.bricolage_digits_600, FontWeight.SemiBold))

/** Lockd In's own palette, available everywhere (Material colors are mapped from it). */
val LocalColors = staticCompositionLocalOf { LightColors }

object Type {
    // Pass 6 (R54): Bricolage for headings, buttons and digits; Archivo for the interface text.
    val display = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp)
    val taskTitle = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    val timer = TextStyle(fontFamily = BricolageDigits, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 56.sp, fontFeatureSettings = "tnum")
    val title = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp)
    val fact = TextStyle(fontFamily = BricolageDigits, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum")
    val body = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val button = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp)
    /** The home greeting under the lock. */
    val greeting = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp)
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
