package app.lockdin

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
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

    /** Menus open in a popup; wait until the item exists before tapping it. */
    private fun pickMenuItem(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text).performClick()
    }

    /** Time only moves when the test says. */
    @Suppress("UNUSED_PARAMETER")
    private fun tick(store: Store) { rule.waitForIdle() }

    /** Opens a setting row (e.g. "Breaks") and picks a value from its menu. */
    private fun pickSetting(row: String, item: String) {
        rule.onNodeWithText(row).performScrollTo().performClick()
        pickMenuItem(item)
    }

    /** Opens "Change rounds and breaks" and picks a value from one of its menus. */
    private fun pickRounds(row: String, item: String) {
        rule.onNodeWithContentDescription("Change rounds and breaks").performScrollTo().performClick()
        pickSetting(row, item)
    }

    @Test fun configureStartFocusBreakResumeFinish() {
        val store = launch()
        rule.onNodeWithText("Start timer").assertExists()
        rule.onNodeWithContentDescription("25 minutes").performClick()
        rule.onNodeWithContentDescription("25 minutes").assertIsSelected()
        pickRounds("Breaks", "5 min")
        rule.onNodeWithText("2 rounds · 5 min break between").assertIsDisplayed()
        rule.onNodeWithText("Ends at 6:55 PM", substring = true).assertIsDisplayed()
        // Typed last: the test activity doesn't resize for the keyboard, which would cover the
        // pinned bar here (the app's activity uses adjustResize + imePadding).
        rule.onNodeWithContentDescription("What are you working on?").performTextInput("TAM 212 · Practice problems")
        rule.onNodeWithText("Start timer").performClick()
        rule.onNodeWithText("Focusing · round 1 of 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Focus: 25 minutes left").assertIsDisplayed()
        rule.onNodeWithText("Timer only. Apps stay available on this phone.").assertIsDisplayed()
        // End now is not on screen until the person asks for a way out.
        rule.onAllNodesWithText("End now").assertCountEquals(0)

        clock.advanceMinutes(25); tick(store)
        rule.onNodeWithText("On a break · round 1 of 2").assertIsDisplayed()
        rule.onNodeWithText("Focus resumes").assertIsDisplayed()
        clock.advanceMinutes(5); tick(store)
        rule.onNodeWithText("Focusing · round 2 of 2").assertIsDisplayed()
        clock.advanceMinutes(25); tick(store)
        rule.onNodeWithText("Session complete").assertIsDisplayed()
        rule.onNodeWithText("50 min").assertIsDisplayed()
        rule.onNodeWithText("Finished as planned.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Start timer").assertExists()
    }

    @Test fun customLengthMatchingAPresetSelectsIt() {
        launch()
        rule.onNodeWithContentDescription("Custom minutes").performClick()
        rule.onNodeWithContentDescription("Minutes").performTextClearance()
        rule.onNodeWithContentDescription("Minutes").performTextInput("25")
        rule.onNodeWithContentDescription("25 minutes").assertIsSelected()
    }

    @Test fun unlockWaitsAndCanBeCancelled_emergencyIsImmediate() {
        val store = launch()
        rule.onNodeWithText("Start timer").performClick()
        rule.onNodeWithText("Need out?").performClick()
        rule.onNodeWithText("Start unlock").performClick()
        rule.onNodeWithText("Unlocking in 01:30", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Cancel unlock").performClick()
        clock.advanceMinutes(2); tick(store)
        rule.onNodeWithContentDescription("Focus:", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Need out?").performClick()
        rule.onNodeWithText("End now").performClick()
        rule.onNodeWithText("End session now").performClick()
        rule.onNodeWithText("Ended with emergency recovery.").assertIsDisplayed()
    }

    @Test fun navigationAndHonestStatus() {
        launch()
        rule.onNodeWithText("Timer only. Apps stay available on this phone.").assertIsDisplayed()
        rule.onNodeWithText("Set up blocking").performClick()
        rule.onNodeWithText("Usage access").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Display over other apps").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.onNodeWithText("No sessions planned yet").assertIsDisplayed()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Appearance").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Study").assertIsDisplayed()
    }

    @Test fun lockedCommitmentOffersOnlyEmergencyRecovery() {
        launch(AppState(commitment = app.lockdin.enforce.Commitment.LOCKED))
        rule.onNodeWithText("Start timer").performClick()
        rule.onNodeWithText("Need out?").assertDoesNotExist()
        rule.onNodeWithText("Emergency recovery").performScrollTo().performClick()
        rule.onNodeWithText("End session now").performClick()
        rule.onNodeWithText("Ended with emergency recovery.").assertIsDisplayed()
    }

    @Test fun profileAppsEditorOpensAndSavesMode() {
        launch()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Allow only these").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Only essential apps", substring = true).assertIsDisplayed()
    }

    @Test fun approvedOnlyProfileWithoutPermissionsStaysTimerOnly() {
        // The test device has not granted usage access: the app must not claim blocking.
        val rules = app.lockdin.enforce.AppRules(app.lockdin.enforce.RuleMode.APPROVED_ONLY, emptySet())
        val s = AppState()
        launch(s.copy(profiles = s.profiles.map { if (it.id == "study") it.copy(rules = rules) else it }))
        rule.onNodeWithText("Blocking needs", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Start timer").assertIsDisplayed()
    }

    @Test fun invalidCustomLengthExplained() {
        launch()
        rule.onNodeWithContentDescription("Custom minutes").performClick()
        rule.onNodeWithContentDescription("Minutes").performTextClearance()
        rule.onNodeWithContentDescription("Minutes").performTextInput("0")
        rule.onNodeWithText("Start timer").performClick()
        rule.onNodeWithText("Focus periods are 1–240 minutes.").performScrollTo().assertIsDisplayed()
    }

    @Test fun scheduleAddToggleEditDelete() {
        launch()
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.onNodeWithText("Add session").performClick()
        rule.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Physics review")
        rule.onNodeWithText("every week at", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Save session").performClick()
        rule.onNodeWithText("Weekly routines").assertIsDisplayed()
        rule.onNodeWithText("Next up").assertIsDisplayed()
        rule.onAllNodesWithText("Physics review").onFirst().assertIsDisplayed()
        rule.onNodeWithContentDescription("Physics review on").performClick()
        rule.onNodeWithText("Off").assertIsDisplayed()
        rule.onNodeWithText("Physics review").performClick()
        rule.onNodeWithText("Edit session").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        rule.onAllNodesWithText("Delete").onLast().performClick()
        rule.onNodeWithText("No sessions planned yet").assertIsDisplayed()
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
        rule.onNodeWithContentDescription("What are you working on?").performTextInput("TAM 212 · Practice problems")
        rule.onNodeWithText("Start timer").performClick()
        clock.advanceMinutes(16); tick(store)
        shot("04-focus-light")
        rule.onNodeWithText("Need out?").performClick()
        rule.onNodeWithText("Start unlock").performClick()
        shot("05-unlock-pending")
    }
    @Test fun screenshotFocusDark() {
        val store = launch(AppState(theme = ThemeChoice.DARK), dark = true)
        rule.onNodeWithContentDescription("25 minutes").performClick()
        pickRounds("Breaks", "5 min")
        rule.onNodeWithText("Start timer").performClick()
        clock.advanceMinutes(27); tick(store)
        shot("06-break-dark")
    }
    @Test fun screenshotRunningLargeText() {
        val store = launch(fontScale = 2f)
        rule.onNodeWithText("Start timer").performClick()
        clock.advanceMinutes(5); tick(store)
        shot("11-focus-font200")
    }
    @Test fun screenshotSetupRoundsOpen() {
        launch()
        rule.onNodeWithContentDescription("What are you working on?").performTextInput("TAM 212 · Problem set 6")
        rule.onNodeWithContentDescription("Change rounds and breaks").performScrollTo().performClick()
        shot("12-setup-rounds-open")
    }
    @Test fun screenshotRecap() {
        val store = launch()
        rule.onNodeWithText("Start timer").performClick()
        clock.advanceMinutes(120); tick(store)
        shot("13-recap")
    }
    @Test fun screenshotSchedule() {
        launch()
        rule.onNodeWithTag("tab-schedule").performClick()
        shot("07-schedule-empty")
        rule.onNodeWithText("Add session").performClick()
        rule.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Physics review")
        shot("08-schedule-editor")
        rule.onNodeWithText("Save session").performClick()
        shot("09-schedule-list")
    }
    @Test fun screenshotDevices() { launch(); rule.onNodeWithText("Set up blocking").performClick(); shot("10-devices") }
}
