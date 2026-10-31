package app.lockdin.lab

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lockdin.ui.LockdInTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders the design-lab directions for review. Not assertions; screenshots go to ci-screenshots. */
@RunWith(AndroidJUnit4::class)
class LabScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) { LockdInTheme(dark) { content() } }
        }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun aSetup() = shot("lab-a-1-setup") { SetupA() }
    @Test fun aFocus() = shot("lab-a-2-focus") { RunA(LabState.FOCUS) }
    @Test fun aBreakDark() = shot("lab-a-3-break-dark", dark = true) { RunA(LabState.BREAK) }
    @Test fun aSetupLarge() = shot("lab-a-4-setup-font200", fontScale = 2f) { SetupA() }
    @Test fun aFocusDark() = shot("lab-a-5-focus-dark", dark = true) { RunA(LabState.FOCUS) }
    @Test fun bSetup() = shot("lab-b-1-setup") { SetupB() }
    @Test fun bFocus() = shot("lab-b-2-focus") { RunB(LabState.FOCUS) }
    @Test fun bBreakDark() = shot("lab-b-3-break-dark", dark = true) { RunB(LabState.BREAK) }
    @Test fun bSetupLarge() = shot("lab-b-4-setup-font200", fontScale = 2f) { SetupB() }
    @Test fun bFocusDark() = shot("lab-b-5-focus-dark", dark = true) { RunB(LabState.FOCUS) }
    @Test fun states() = shot("lab-states") { StatesSheet() }
}
