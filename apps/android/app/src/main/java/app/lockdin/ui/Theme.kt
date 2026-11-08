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

// Type (R56, owner 2026-11-08): Literata (TypeTogether, OFL; design/fonts/Literata-OFL.txt), a book
// serif made for long reading, picked by the owner from a side-by-side on the home screen. It fits
// the library/study feel. Static instances from the variable font: text cuts at optical size 12-14,
// display cuts at 36 for headings and the greeting, 48 for clock digits.
val LiterataText = FontFamily(
    Font(R.font.literata_text_400, FontWeight.Normal),
    Font(R.font.literata_text_500, FontWeight.Medium),
    Font(R.font.literata_text_600, FontWeight.SemiBold),
)
/** Literata italic (static instance opsz 36, wght 500; OFL) for the streak line (R63). */
val LiterataItalic = FontFamily(Font(app.lockdin.R.font.literata_display_italic_500, FontWeight.Medium, androidx.compose.ui.text.font.FontStyle.Italic))

val LiterataDisplay = FontFamily(
    Font(R.font.literata_text_500, FontWeight.Medium),
    Font(R.font.literata_display_600, FontWeight.SemiBold),
    Font(R.font.literata_display_700, FontWeight.Bold),
    Font(R.font.literata_display_700, FontWeight.ExtraBold),
)
val LiterataDigits = FontFamily(
    Font(R.font.literata_digits_600, FontWeight.SemiBold),
    Font(R.font.literata_digits_600, FontWeight.ExtraBold),
)

/** Lockd In's own palette, available everywhere (Material colors are mapped from it). */
val LocalColors = staticCompositionLocalOf { LightColors }

object Type {
    // Pass 6 (R54): LiterataDisplay for headings, buttons and digits; LiterataText for the interface text.
    val display = TextStyle(fontFamily = LiterataDisplay, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp)
    val taskTitle = TextStyle(fontFamily = LiterataDisplay, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    val timer = TextStyle(fontFamily = LiterataDigits, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 56.sp, fontFeatureSettings = "lnum, tnum")
    val title = TextStyle(fontFamily = LiterataDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp)
    val fact = TextStyle(fontFamily = LiterataDigits, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "lnum, tnum")
    val body = TextStyle(fontFamily = LiterataText, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontFamily = LiterataText, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = LiterataText, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val button = TextStyle(fontFamily = LiterataDisplay, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp)
    /** The home greeting under the lock. */
    val greeting = TextStyle(fontFamily = LiterataDisplay, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp)
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
