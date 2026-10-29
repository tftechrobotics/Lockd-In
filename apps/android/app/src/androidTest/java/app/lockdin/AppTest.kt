package app.lockdin

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lockdin.state.AppClock
import app.lockdin.state.AppState
import app.lockdin.state.Store
import app.lockdin.state.ThemeChoice
import app.lockdin.ui.LockdInApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** A clock the test moves by hand; Compose recomposes when it moves. */
class FakeClock(start: Long) : AppClock {
    private var t by mutableLongStateOf(start)
    override fun now() = t
    override val ticks = false
    fun advanceMinutes(m: Long) { t += m * 60_000 }
}

@RunWith(AndroidJUnit4::class)
class AppTest {
    @get:Rule val rule = createComposeRule()

    private val zone = ZoneId.of("America/Chicago")
    private val clock = FakeClock(ZonedDateTime.of(2026, 11, 3, 18, 0, 0, 0, zone).toInstant().toEpochMilli())

    private fun launch(state: AppState = AppState(), dark: Boolean = false, fontScale: Float = 1f): Store {
        val store = Store(state) {}
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                LockdInApp(store, clock, zone, dark)
            }
        }
        return store
    }

    /** Time only moves when the test says. */
    @Suppress("UNUSED_PARAMETER")
    private fun tick(store: Store) { rule.waitForIdle() }

    @Test fun configureStartFocusBreakResumeFinish() {
        val store = launch()
        rule.onNodeWithText("What’s the move?").assertIsDisplayed()
        rule.onNode(hasText("What are you working on? (optional)")).performTextInput("TAM 212 · Practice problems")
        rule.onNodeWithText("25 min").performClick()
        rule.onNodeWithContentDescription("Break: No break. Change", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("5 min").performClick()
        rule.onNodeWithText("Lock in").performScrollTo().performClick()
        rule.onNodeWithText("Locked in.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Focus 1 of 2: 25 minutes left").assertIsDisplayed()

        clock.advanceMinutes(25); tick(store)
        rule.onNodeWithText("On a break.").assertIsDisplayed()
        clock.advanceMinutes(5); tick(store)
        rule.onNodeWithText("Locked in.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Focus 2 of 2", substring = true).assertIsDisplayed()
        clock.advanceMinutes(25); tick(store)
        rule.onNodeWithText("Session complete.").assertIsDisplayed()
        rule.onNodeWithText("50 min").assertIsDisplayed()
        rule.onNodeWithText("Finished as planned.").assertIsDisplayed()
        rule.onNodeWithText("Done").performScrollTo().performClick()
        rule.onNodeWithText("What’s the move?").assertIsDisplayed()
    }

    @Test fun unlockWaitsAndCanBeCancelled_emergencyIsImmediate() {
        val store = launch()
        rule.onNodeWithText("Lock in").performScrollTo().performClick()
        rule.onNodeWithText("Need out?").performScrollTo().performClick()
        rule.onNodeWithText("Start 90-second unlock").performScrollTo().performClick()
        rule.onNodeWithText("Unlocking in 01:30", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Cancel unlock").performClick()
        clock.advanceMinutes(2); tick(store)
        rule.onNodeWithText("Locked in.").assertIsDisplayed()
        rule.onNodeWithText("Emergency: end now").performScrollTo().performClick()
        rule.onNodeWithText("End session now").performClick()
        rule.onNodeWithText("Ended with emergency recovery.").assertIsDisplayed()
    }

    @Test fun navigationAndHonestStatus() {
        launch()
        rule.onNodeWithText("Blocking not set up").assertIsDisplayed()
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.onNodeWithText("No sessions planned", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Appearance").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Study").assertIsDisplayed()
    }

    @Test fun invalidCustomLengthExplained() {
        launch()
        rule.onNodeWithText("Custom").performClick()
        rule.onNode(hasText("Minutes")).performTextInput("0")
        rule.onNodeWithText("Lock in").performScrollTo().performClick()
        rule.onNodeWithText("Focus periods are 1–240 minutes.").assertIsDisplayed()
    }

    // ---- Screenshots for design review (pulled by CI; not assertions) ----
    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun screenshotSetupLight() { launch(); shot("01-setup-light") }
    @Test fun screenshotSetupDark() { launch(AppState(theme = ThemeChoice.DARK), dark = true); shot("02-setup-dark") }
    @Test fun screenshotSetupLargeText() { launch(fontScale = 2f); shot("03-setup-font200") }
    @Test fun screenshotFocusAndBreak() {
        val store = launch()
        rule.onNode(hasText("What are you working on? (optional)")).performTextInput("TAM 212 · Practice problems")
        rule.onNodeWithText("Lock in").performScrollTo().performClick()
        clock.advanceMinutes(16); tick(store)
        shot("04-focus-light")
        rule.onNodeWithText("Need out?").performScrollTo().performClick()
        rule.onNodeWithText("Start 90-second unlock").performScrollTo().performClick()
        shot("05-unlock-pending")
    }
    @Test fun screenshotFocusDark() {
        val store = launch(AppState(theme = ThemeChoice.DARK), dark = true)
        rule.onNodeWithText("25 min").performClick()
        rule.onNodeWithContentDescription("Break: No break. Change", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("5 min").performClick()
        rule.onNodeWithText("Lock in").performScrollTo().performClick()
        clock.advanceMinutes(27); tick(store)
        shot("06-break-dark")
    }
    @Test fun screenshotSchedule() {
        launch()
        rule.onNodeWithTag("tab-schedule").performClick()
        shot("07-schedule-empty")
        rule.onNodeWithText("Plan a session").performClick()
        shot("08-schedule-editor")
    }
}
