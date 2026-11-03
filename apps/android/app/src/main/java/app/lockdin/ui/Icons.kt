// Lockd In's stroke icon set (same 24-unit paths as the web app's Icon.tsx), so
// all platforms share one icon language without an extra icon library.
package app.lockdin.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class LIcon(val path: String) {
    Laptop("M4 6.5h16v9.5H4zM2 18.5h20"),
    Phone("M7.5 2.5h9v19h-9zM11 18.5h2"),
    Calendar("M4 5.5h16v15H4zM4 10h16M8.5 3v4M15.5 3v4"),
    Coffee("M4 9h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM16 10.5h1.5a2.5 2.5 0 0 1 0 5H16M8 3.5c0 1.2 1 1.6 1 2.8M12 3.5c0 1.2 1 1.6 1 2.8"),
    Book("M4 5.5c2.5-1.3 5.5-1.3 8 0v14c-2.5-1.3-5.5-1.3-8 0zM12 5.5c2.5-1.3 5.5-1.3 8 0v14c-2.5-1.3-5.5-1.3-8 0z"),
    Gear("M18.97 10.21 L21.27 10.47 L21.27 13.53 L18.97 13.79 L18.20 15.67 L19.64 17.48 L17.48 19.64 L15.67 18.20 L13.79 18.97 L13.53 21.27 L10.47 21.27 L10.21 18.97 L8.33 18.20 L6.52 19.64 L4.36 17.48 L5.80 15.67 L5.03 13.79 L2.73 13.53 L2.73 10.47 L5.03 10.21 L5.80 8.33 L4.36 6.52 L6.52 4.36 L8.33 5.80 L10.21 5.03 L10.47 2.73 L13.53 2.73 L13.79 5.03 L15.67 5.80 L17.48 4.36 L19.64 6.52 L18.20 8.33Z M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 1 0 0-6.8"),
    Chevron("M9 6l6 6-6 6"),
    ChevronDown("M6 9l6 6 6-6"),
    ChevronUp("M6 15l6-6 6 6"),
    Back("M15 6l-6 6 6 6"),
    Arrow("M4.5 12h15M13.5 6l6 6-6 6"),
    Check("M5 12.5l4.5 4.5L19 7.5"),
    Dash("M6 12h12"),
    Info("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18M12 11v5.5M12 7.5h.01"),
    Plus("M12 5v14M5 12h14"),
    Minus("M5 12h14"),
    Close("M6 6l12 12M18 6L6 18"),
    Clock("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18M12 7.5V12l3 2"),
    Repeat("M4 11a7 7 0 0 1 12.6-4.2L19 9M19 4v5h-5M20 13a7 7 0 0 1-12.6 4.2L5 15M5 20v-5h5"),
    Focus("M7 11V8a5 5 0 0 1 10 0v3M5 11h14v10H5z"),
    History("M3.5 12a8.5 8.5 0 1 0 2.5-6M3.5 4v4h4M12 7.5V12l3 2"),
    Play("M8 5.5v13l10-6.5z"),
    Pause("M8.5 5.5v13M15.5 5.5v13"),
    Next("M6 6l8.5 6L6 18zM18 5.5v13"),
    Sound("M4 9.5h3.5l4.5-4v13l-4.5-4H4zM15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"),
    Devices("M2 4h10v7H2zM5 14h4M7 11v3M13 6h3v9h-3z"),
    Person("M12 4a4 4 0 1 0 0 8 4 4 0 1 0 0-8M4.5 20.5a7.5 7.5 0 0 1 15 0"),
    Shield("M12 3l7.5 3v5.5c0 4.6-3.2 8.2-7.5 9.5-4.3-1.3-7.5-4.9-7.5-9.5V6z"),
    Bell("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0"),
    Brush("M12 3.5a8.5 8.5 0 1 0 0 17c1.4 0 2-1 1.6-2.2-.5-1.4.4-2.8 1.9-2.8H18a2.5 2.5 0 0 0 2.5-2.5A8.5 8.5 0 0 0 12 3.5M7.5 11h.01M10 7.5h.01M14.5 7.5h.01");

    val vector: ImageVector by lazy {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
}

/** Decorative unless [description] is given (then announced by TalkBack). */
@Composable
fun LockdIcon(icon: LIcon, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalColors.current.ink, description: String? = null) {
    Icon(imageVector = icon.vector, contentDescription = description, tint = tint, modifier = modifier.size(size))
}

