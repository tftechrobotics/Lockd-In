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
    Gear("M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"),
    Chevron("M9 6l6 6-6 6"),
    ChevronDown("M6 9l6 6 6-6"),
    Back("M15 6l-6 6 6 6"),
    Arrow("M4.5 12h15M13.5 6l6 6-6 6"),
    Check("M5 12.5l4.5 4.5L19 7.5"),
    Dash("M6 12h12"),
    Info("M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18M12 11v5.5M12 7.5h.01"),
    Plus("M12 5v14M5 12h14"),
    Minus("M5 12h14"),
    Focus("M7 11V8a5 5 0 0 1 10 0v3M5 11h14v10H5z"),
    Devices("M2 4h10v7H2zM5 14h4M7 11v3M13 6h3v9h-3z");

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

