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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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

    /** Launches the app. Unless `home`, goes from the idle lock to the session setup, as most tests start there. */
    /** In-memory history for each test (never the app's real file). */
    private var hist = app.lockdin.state.HistoryStore(emptyList()) {}

    private fun launch(state: AppState = AppState(), dark: Boolean = false, fontScale: Float = 1f, home: Boolean = false): Store {
        val store = Store(state) {}
        hist = app.lockdin.state.HistoryStore(emptyList()) {}
        store.clock = { clock.now() }
        store.onLeft = { a, st, now -> hist.record(a, st, now) }
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                LockdInApp(store, clock, zone, dark, history = hist)
            }
        }
        if (!home && state.active == null) rule.onNodeWithText("New session").performClick()
        return store
    }

    private fun openDetails() = rule.onNodeWithText("Details and options").performScrollTo().performClick()
    /** The recap shows its summary directly now; kept as a no-op step for readability. */
    private fun openSummary() = rule.waitForIdle()

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
        // The menu's popup closes with an animation; wait for it before checking the pinned bar.
        rule.waitForIdle()
        rule.onNodeWithText("Ends at 6:55", substring = true, useUnmergedTree = true).assertIsDisplayed()
        // Typed last: the test activity doesn't resize for the keyboard, which would cover the
        // pinned bar here (the app's activity uses adjustResize + imePadding).
        rule.onNodeWithContentDescription("What are you working on?").performTextInput("TAM 212 · Practice problems")
        rule.onNodeWithText("Start timer").performClick()
        rule.onNodeWithText("Focusing · round 1 of 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Focus: 25 minutes left").assertIsDisplayed()
        rule.onNodeWithText("Next break", substring = true).assertIsDisplayed()
        // The phone view is just the lock and what's next; details and End now are one tap away.
        rule.onAllNodesWithText("End now").assertCountEquals(0)
        rule.onAllNodesWithText("Timer only. Apps stay available on this phone.").assertCountEquals(0)
        openDetails()
        rule.onNodeWithText("Timer only. Apps stay available on this phone.").performScrollTo().assertIsDisplayed()

        clock.advanceMinutes(25); tick(store)
        // Details are open and scrolled; the status line is above them.
        rule.onNodeWithText("On a break · round 1 of 2").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Focus resumes", substring = true).onFirst().assertExists()
        clock.advanceMinutes(5); tick(store)
        rule.onNodeWithText("Focusing · round 2 of 2").performScrollTo().assertIsDisplayed()
        clock.advanceMinutes(25); tick(store)
        rule.onNodeWithText("Session complete").assertIsDisplayed()
        rule.onNodeWithText("Focused 50 min").assertIsDisplayed()
        openSummary()
        rule.onNodeWithText("Finished as planned.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("You didn’t open any blocked apps or sites.").assertDoesNotExist()
        rule.onNodeWithText("Timer only: nothing was blocked on this phone.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        // Back to the idle lock.
        rule.onNodeWithText("New session").assertExists()
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
        openDetails()
        rule.onNodeWithText("Need out?").performScrollTo().performClick()
        rule.onNodeWithText("Start unlock").performScrollTo().performClick()
        rule.onNodeWithText("Unlocking in 01:30", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Cancel unlock").performScrollTo().performClick()
        clock.advanceMinutes(2); tick(store)
        rule.onNodeWithContentDescription("Focus:", substring = true).assertExists()
        rule.onNodeWithText("Need out?").performScrollTo().performClick()
        rule.onNodeWithText("End now").performScrollTo().performClick()
        rule.onNodeWithText("End session now").performClick()
        openSummary()
        rule.onNodeWithText("Ended with emergency recovery.").performScrollTo().assertIsDisplayed()
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
        openDetails()
        rule.onNodeWithText("Need out?").assertDoesNotExist()
        rule.onNodeWithText("Emergency recovery").performScrollTo().performClick()
        rule.onNodeWithText("End session now").performClick()
        openSummary()
        rule.onNodeWithText("Ended with emergency recovery.").performScrollTo().assertIsDisplayed()
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

    @Test fun accessibilityIsExplainedBeforeSettingsOpen() {
        launch()
        rule.onNodeWithText("Set up blocking").performClick()
        rule.onNodeWithText("Accessibility (recommended)").performScrollTo().performClick()
        rule.onNodeWithText("Use Accessibility for blocking?").assertIsDisplayed()
        rule.onNodeWithText("doesn’t read what you type", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        rule.onNodeWithText("Use Accessibility for blocking?").assertDoesNotExist()
    }

    @Test fun websitesTabValidatesAndAdds() {
        launch()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Websites").performClick()
        rule.onNodeWithContentDescription("Website to block").performTextInput("not a site")
        rule.onNodeWithText("Add").performClick()
        rule.onNodeWithText("Enter a site like youtube.com").assertIsDisplayed()
        rule.onNodeWithContentDescription("Website to block").performTextClearance()
        rule.onNodeWithContentDescription("Website to block").performTextInput("https://www.YouTube.com/watch")
        rule.onNodeWithText("Add").performClick()
        // The field is empty again (its placeholder also says youtube.com); the list has the new row.
        rule.onNodeWithText("Remove").assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("1 website blocked during focus").assertIsDisplayed()
    }

    @Test fun websitesAllowOnlyModeSaves() {
        launch()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Websites").performClick()
        rule.onAllNodesWithText("Allow only these").onFirst().performClick()
        rule.onNodeWithText("No sites allowed yet", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Website to allow").performTextInput("wikipedia.org")
        rule.onNodeWithText("Add").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Only 1 website allowed during focus").assertIsDisplayed()
    }

    @Test fun newProfileOpensEditorAndCanBeDeleted() {
        launch()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("New profile").performClick()
        rule.onNodeWithContentDescription("New profile name").performTextInput("Lab report")
        rule.onNodeWithText("Create").performClick()
        // The editor opens straight away, with the essentials note.
        rule.onNodeWithText("Never blocked", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        rule.onNodeWithText("Delete “Lab report”?").assertIsDisplayed()
        rule.onAllNodesWithText("Delete").onLast().performClick()
        rule.onNodeWithText("Lab report").assertDoesNotExist()
        rule.onNodeWithText("Study").assertIsDisplayed()
    }

    @Test fun startSequencePlaysOnceForAManualStartOnly() {
        val tester = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        val store = Store(AppState()) {}
        tester.setContent { LockdInApp(store, clock, zone, false) }
        rule.onNodeWithText("New session").performClick()
        val before = app.lockdin.ui.StartIntro.plays
        rule.onNodeWithText("Start timer").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("plays once for the manual start", before + 1, app.lockdin.ui.StartIntro.plays)
        // Rotation / activity re-creation rebuilds the screen: no replay.
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("no replay after re-creation", before + 1, app.lockdin.ui.StartIntro.plays)
        rule.onNodeWithText("Focusing", substring = true).assertExists()
    }

    @Test fun startSequencePlaysOnEveryManualStart() {
        // Owner report: the dial turned only for the first session. Start, finish, start again
        // (Done → New session) and once more with "Same again".
        val store = launch(AppState(last = app.lockdin.state.LastChoice(25, 5, 1, "study")))
        val p0 = app.lockdin.ui.StartIntro.plays; val d0 = app.lockdin.ui.StartIntro.drawn
        rule.onNodeWithText("Start timer").performClick(); rule.waitForIdle()
        clock.advanceMinutes(26); tick(store)
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("New session").performClick()
        rule.onNodeWithText("Start timer").performClick(); rule.waitForIdle()
        clock.advanceMinutes(26); tick(store)
        rule.onNodeWithText("Same again").performClick(); rule.waitForIdle()
        org.junit.Assert.assertEquals("requests consumed", p0 + 3, app.lockdin.ui.StartIntro.plays)
        org.junit.Assert.assertEquals("sequences drawn", d0 + 3, app.lockdin.ui.StartIntro.drawn)
    }

    @Test fun sessionStartedOutsideTheAppDoesNotPlayTheSequence() {
        // As after a scheduled start: the session already exists when the app is opened.
        val store = Store(AppState()) {}
        store.startSession(clock.now(), app.lockdin.engine.Session.roundsPlan(1, 25, 5), "Planned", "study", store.state.last)
        val before = app.lockdin.ui.StartIntro.plays
        launch(store.state)
        rule.waitForIdle()
        org.junit.Assert.assertEquals(before, app.lockdin.ui.StartIntro.plays)
    }

    @Test fun startFeedbackSettingsDefaultHapticsOnSoundOff() {
        launch(home = true)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Haptic clicks").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Click sound").performScrollTo().assertIsDisplayed()
        val s = AppState()
        org.junit.Assert.assertTrue(s.startHaptics)
        org.junit.Assert.assertFalse(s.startSound)
    }

    @Test fun finishedSessionAppearsInHistoryAndCanBeDeleted() {
        val store = launch(AppState(last = app.lockdin.state.LastChoice(25, 5, 1, "study")))
        rule.onNodeWithText("Start timer").performClick()
        clock.advanceMinutes(26); tick(store)
        rule.onNodeWithText("Done").performClick()
        org.junit.Assert.assertEquals(1, hist.entries.size)
        org.junit.Assert.assertEquals(app.lockdin.engine.Outcome.COMPLETED, hist.entries.single().outcome)
        rule.onNodeWithTag("tab-history").performClick()
        rule.onNodeWithText("This week").assertIsDisplayed()
        rule.onNodeWithText("25 min focused").assertIsDisplayed()
        rule.onNodeWithText("Focus session").performClick()
        rule.onNodeWithText("Finished as planned", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Delete this session").performScrollTo().performClick()
        rule.onAllNodesWithText("Delete").onLast().performClick()
        rule.onNodeWithText("No sessions yet").assertIsDisplayed()
        org.junit.Assert.assertTrue(hist.entries.isEmpty())
    }

    @Test fun historyIsEmptyAtFirst() {
        launch(home = true)
        rule.onNodeWithTag("tab-history").performClick()
        rule.onNodeWithText("No sessions yet").assertIsDisplayed()
    }

    @Test fun presetIsSavedAndStartsInOneTapFromHome() {
        val store = launch()
        rule.onNode(hasSetTextAction()).performTextInput("Physics review")
        rule.onNodeWithText("Save as preset").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Preset name").assertExists()
        rule.onAllNodesWithText("Save").onLast().performClick()
        org.junit.Assert.assertEquals("Physics review", store.state.presets.single().name)
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Start a preset").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Physics review", substring = true).performScrollTo().performClick()
        val a = store.state.active
        org.junit.Assert.assertNotNull(a)
        org.junit.Assert.assertEquals("Physics review", a!!.intention)
        org.junit.Assert.assertEquals(store.state.presets.single().id, a.presetId)
    }

    @Test fun presetStepsShowDuringTheSession() {
        val p = app.lockdin.state.Preset("p1", "Essay", 40, 0, 1, "study", task = "Essay", steps = listOf("Outline", "Draft intro"))
        val store = launch(AppState(presets = listOf(p)), home = true)
        rule.onNodeWithText("Essay", substring = true).performScrollTo().performClick()
        openDetails()
        rule.onNodeWithText("Steps").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Draft intro").performScrollTo().performClick()
        org.junit.Assert.assertEquals("p1", store.state.active?.presetId)
    }

    @Test fun presetEditorChangesDueDateFreeFieldsAndDeletes() {
        val p = app.lockdin.state.Preset("p1", "Essay", 40, 0, 1, "study")
        val store = launch(AppState(presets = listOf(p)), home = true)
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Essay").performScrollTo().performClick()
        rule.onNodeWithContentDescription("New step").performTextInput("Outline")
        rule.onNodeWithText("Add").performClick()
        rule.onNodeWithText("Save").performClick()
        org.junit.Assert.assertEquals(listOf("Outline"), store.state.presets.single().steps)
        rule.onNodeWithText("Essay").performScrollTo().performClick()
        rule.onNodeWithText("Delete").performClick()
        rule.onAllNodesWithText("Delete").onLast().performClick()
        org.junit.Assert.assertTrue(store.state.presets.isEmpty())
    }

    @Test fun historySettingsRetentionAndDeleteAll() {
        val store = launch(home = true)
        hist.add(app.lockdin.engine.HistoryEntry("e1", clock.now() - 3_600_000, clock.now() - 1_800_000, clock.now() - 1_800_000,
            1_800_000, 1_800_000, 1, 1, "Read", "study", "Study", app.lockdin.engine.Outcome.COMPLETED), 365, clock.now())
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Keep history").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1 session. Opens Android’s share sheet.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Delete all history").performScrollTo().performClick()
        rule.onAllNodesWithText("Delete").onLast().performClick()
        org.junit.Assert.assertTrue(hist.entries.isEmpty())
        org.junit.Assert.assertEquals(365, store.state.historyDays)
    }

    @Test fun profileSoundTabSavesAmbientAndPlaylist() {
        val store = launch(home = true)
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Sound").performClick()
        rule.onNodeWithText("Rain").performClick()
        rule.onNodeWithContentDescription("Playlist link").performTextInput("not a playlist")
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Paste a playlist link from Spotify", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Playlist link").performTextClearance()
        rule.onNodeWithContentDescription("Playlist link").performTextInput("https://open.spotify.com/playlist/37i9dQZF1DX8NTLI2TtZa6")
        rule.onNodeWithText("Controls your Spotify app", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()
        val a = store.state.profiles.first { it.id == "study" }.audio
        org.junit.Assert.assertEquals(app.lockdin.audio.Ambient.RAIN, a.ambient)
        org.junit.Assert.assertEquals(app.lockdin.audio.Music.SPOTIFY, a.musicProvider)
    }

    @Test fun sessionShowsSoundControlsForItsProfile() {
        val base = AppState(last = app.lockdin.state.LastChoice(25, 5, 1, "study"))
        val audio = app.lockdin.state.ProfileAudio(app.lockdin.audio.Ambient.WAVES, musicLink = "https://music.youtube.com/playlist?list=PL1", autoPlay = false)
        launch(base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(audio = audio) else it }))
        rule.onNodeWithText("Start timer").performClick()
        rule.onNodeWithContentDescription("Play waves").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Play or pause YouTube Music").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Next track in YouTube Music").assertExists()
        rule.onNodeWithText("Plays in YouTube Music", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun scheduleOffersCalendarSuggestionsOnlyWhenAsked() {
        launch(home = true)
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.onNodeWithText("Find free time in your calendar").assertIsDisplayed()
        rule.onNodeWithText("never changes your calendar", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Suggest times from my calendar").performScrollTo().assertIsDisplayed()
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
        // The software keyboard can cover this summary on the test device; checking it exists is enough here.
        rule.onNodeWithText("every week at", substring = true).assertExists()
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
        openDetails()
        rule.onNodeWithText("Need out?").performScrollTo().performClick()
        rule.onNodeWithText("Start unlock").performScrollTo().performClick()
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
    @Test fun idleHomeShowsTimeAndNextSession() {
        val start = java.time.LocalTime.of(19, 0)
        launch(AppState(schedule = listOf(app.lockdin.engine.ScheduledSession("p", "Physics review", java.time.DayOfWeek.entries.toSet(), start, 40, 10, 2, "study"))), home = true)
        rule.onNodeWithContentDescription("It's 6:00", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Physics review").assertIsDisplayed()
        rule.onNodeWithText("in 1 h", substring = true).assertIsDisplayed()
        rule.onNodeWithText("New session").performClick()
        rule.onNodeWithText("Start timer").assertExists()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("New session").assertExists()
    }
    @Test fun recapListsBlockedAttemptsAndReturnsHomeAfter30s() {
        val rules = app.lockdin.enforce.AppRules(sites = setOf("youtube.com"))
        val s0 = AppState()
        val store = launch(s0.copy(profiles = s0.profiles.map { if (it.id == "study") it.copy(rules = rules) else it }))
        rule.onNodeWithText("Start timer").performClick()
        val id = store.state.active!!.id
        rule.runOnIdle { repeat(3) { store.recordBlocked(id, "site:youtube.com") } }
        clock.advanceMinutes(41); tick(store)
        rule.onNodeWithText("Blocked during this session").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("youtube.com").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("3 times").assertExists()
        rule.onNodeWithText("Back to home in 30 s").assertExists()
        clock.advanceMinutes(1); tick(store)
        rule.onNodeWithText("New session").assertIsDisplayed()
    }
    @Test fun autoStartIsExplainedBeforeItTurnsOn() {
        launch()
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.onNodeWithText("Add session").performClick()
        rule.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Physics review")
        rule.onNodeWithText("Start automatically").performScrollTo().performClick()
        rule.onNodeWithText("Start this session automatically?").assertIsDisplayed()
        rule.onNodeWithText("Turn on").performClick()
        rule.onNodeWithText("Starts and blocks automatically").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Save session").performClick()
        rule.onAllNodesWithText("Starts automatically", substring = true).onFirst().assertExists()
    }

    @Test fun allNotificationsCanBeTurnedOffAndOn() {
        val store = launch()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Allow notifications").performScrollTo().performClick()
        org.junit.Assert.assertFalse(store.state.reminders.notificationsOn)
        rule.onNodeWithText("Reminders before sessions").assertDoesNotExist()
        // (The caption below depends on whether the test device allows notifications.)
        rule.onNodeWithText("Allow notifications").performScrollTo().performClick()
        org.junit.Assert.assertTrue(store.state.reminders.notificationsOn)
        rule.onNodeWithText("Blocking problems").performScrollTo().performClick()
        org.junit.Assert.assertFalse(store.state.reminders.blockingAlertsOn)
    }

    @Test fun reminderSettingsAndQuietHours() {
        val store = launch()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Quiet hours").performScrollTo().performClick()
        rule.onNodeWithText("Quiet from").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Follow-up after a missed session").performScrollTo().performClick()
        rule.runOnIdle {
            assert(store.state.reminders.quietStart != null)
            assert(!store.state.reminders.missedFollowUpOn)
        }
    }

    @Test fun lastSessionStaysOnHomeUntilDismissed() {
        val store = launch()
        rule.onNodeWithText("Start timer").performClick()
        clock.advanceMinutes(41); tick(store)
        clock.advanceMinutes(1); tick(store) // recap times out
        rule.onNodeWithText("Last session", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Last session", substring = true).performClick()
        rule.onNodeWithText("Session complete").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithContentDescription("Dismiss last session").performClick()
        rule.onAllNodesWithText("Last session", substring = true).assertCountEquals(0)
    }

    /**
     * Frame-by-frame capture of the real ClockLock start sequence (30 fps, test clock), for the
     * design review video. Blocking confirmation is simulated at 250 ms; in the app it is the
     * service's PROTECTING report. Frames: files/screens/seq/<theme>_NNN.png.
     */
    @Test fun recordStartSequenceFrames() {
        val dark = mutableStateOf(false)
        val locked = mutableStateOf(false)
        val key = mutableStateOf<String?>(null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            app.lockdin.ui.LockdInTheme(dark.value) {
                val c = app.lockdin.ui.LocalColors.current
                androidx.compose.foundation.layout.Box(
                    Modifier.background(c.ground).padding(24.dp).testTag("seq"),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.runtime.key(key.value) {
                        app.lockdin.ui.ClockLock(
                            app.lockdin.ui.TimerState.FOCUS, 40 * 60_000L, 40 * 60_000L, locked.value,
                            "Focus: 40 minutes left", "left this round", width = 260.dp,
                            introFor = key.value, reducedMotion = false,
                        )
                    }
                }
            }
        }
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens/seq").apply { mkdirs() }
        for (theme in listOf("light", "dark")) {
            dark.value = theme == "dark"; locked.value = false; key.value = null
            rule.mainClock.advanceTimeBy(500)
            key.value = "demo-$theme"
            for (i in 0 until 60) {
                if (i * 33 >= 250) locked.value = true
                rule.mainClock.advanceTimeBy(33)
                val bmp = rule.onNodeWithTag("seq").captureToImage().asAndroidBitmap()
                File(dir, "${theme}_%03d.png".format(i)).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun widgetShowsNextOrRunningSession() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val now = clock.now()
        val idle = app.lockdin.widget.LockdInWidget.content(AppState(), now, zone)
        org.junit.Assert.assertEquals("Nothing planned", idle.title)
        val store = Store(AppState()) {}
        store.startSession(now, app.lockdin.engine.Session.roundsPlan(1, 40, 5), "Physics", "study", store.state.last)
        val running = app.lockdin.widget.LockdInWidget.content(store.state, now + 60_000, zone)
        org.junit.Assert.assertEquals("Focusing", running.label)
        org.junit.Assert.assertEquals(now + 40 * 60_000, running.countdownTo)
        // Render the real layout for the design review (light theme of the test device).
        val dir = File(ctx.filesDir, "screens").apply { mkdirs() }
        listOf("20-widget-idle" to idle, "21-widget-running" to running).forEach { (name, c) ->
            val parent = android.widget.FrameLayout(ctx)
            val view = app.lockdin.widget.LockdInWidget.views(ctx, c, now).apply(ctx, parent)
            val w = (300 * ctx.resources.displayMetrics.density).toInt(); val h = (170 * ctx.resources.displayMetrics.density).toInt()
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.rgb(200, 205, 215))
            view.draw(android.graphics.Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun screenshotHome() { launch(home = true); shot("00-home-light") }
    @Test fun screenshotHomeDark() { launch(AppState(theme = ThemeChoice.DARK), dark = true, home = true); shot("00-home-dark") }
    @Test fun screenshotDevices() { launch(); rule.onNodeWithText("Set up blocking").performClick(); shot("10-devices") }
}
