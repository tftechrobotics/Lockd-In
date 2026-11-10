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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.semantics.getOrNull
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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.performScrollToNode
import org.junit.runner.RunWith
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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

    /** Waits for text; on timeout the failure includes the screen's semantics tree, so CI logs show what was there. */
    private fun waitForText(text: String, ms: Long) {
        try {
            rule.waitUntil(ms) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            val shown = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
                .mapNotNull { n ->
                    n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString(" ")
                        ?: n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString(" ")
                }.joinToString(" | ")
            throw AssertionError("'$text' not shown. Screen: $shown", e)
        }
    }

    /** Settings pages (owner 2026-11-03): the old section names map to the page that holds them. */
    private val sectionPage = mapOf(
        "Starting a session" to "Sessions", "New session defaults" to "Sessions", "Check-ins and coaching" to "Sessions",
        "Appearance" to "Look and messages", "Look and background" to "Look and messages", "Messages" to "Look and messages",
        "Calendar" to "Calendar and places", "Places" to "Calendar and places",
    )

    private fun openSection(title: String) {
        if (rule.onAllNodesWithContentDescription("Back to Settings").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithContentDescription("Back to Settings").performScrollTo().performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("settings-${sectionPage[title] ?: title}").performScrollTo().performClick()
        rule.waitForIdle()
    }

    /**
     * [viewport] renders the app at an exact device size in dp (e.g. a Galaxy Z Flip, 407 × 994 dp), at
     * whatever density makes it fit this emulator's screen, so one emulator can check several phone
     * shapes. Layout is decided in dp, so the result matches the real shape; only the pixel scale differs.
     */
    private fun launch(state: AppState = AppState(), dark: Boolean = false, fontScale: Float = 1f, home: Boolean = false,
                       viewport: androidx.compose.ui.unit.DpSize? = null): Store {
        val store = Store(state) {}
        app.lockdin.ui.SettingsNav.page = null
        hist = app.lockdin.state.HistoryStore(emptyList()) {}
        store.clock = { clock.now() }
        store.onLeft = { a, st, now -> hist.record(a, st, now) }
        rule.setContent {
            val d = LocalDensity.current
            if (viewport == null) {
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                    LockdInApp(store, clock, zone, dark, history = hist)
                }
            } else {
                val conf = androidx.compose.ui.platform.LocalConfiguration.current
                val wPx = conf.screenWidthDp * d.density; val hPx = conf.screenHeightDp * d.density
                val dens = minOf(d.density, wPx / viewport.width.value, hPx / viewport.height.value)
                CompositionLocalProvider(LocalDensity provides Density(dens, fontScale)) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredSize(viewport).testTag("viewport")) {
                        LockdInApp(store, clock, zone, dark, history = hist)
                    }
                }
            }
        }
        if (!home && state.active == null) rule.onNodeWithTag("start-session").performClick()
        return store
    }

    /** Captures just the viewport box (see [launch]). */
    private fun vshot(name: String) {
        rule.waitForIdle()
        val bmp = runCatching { rule.onNodeWithTag("viewport").captureToImage() }.getOrElse { rule.waitForIdle(); rule.onNodeWithTag("viewport").captureToImage() }.asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    /** Opens the collapsed "Session options" row on setup, if it isn't open yet. */
    private fun openOptions() {
        if (rule.onAllNodesWithContentDescription("Change rounds and breaks").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("Session options").performScrollTo().performClick()
    }

    /** Opens "Change rounds and breaks" and picks a value from one of its menus. */
    private fun pickRounds(row: String, item: String) {
        openOptions()
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
        rule.onNodeWithText("Round 1 of 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Focus: 25 minutes left").assertIsDisplayed()
        rule.onNodeWithText("break at", substring = true).assertIsDisplayed()
        // The phone view: task, this phone's status, the lock and what's next; End now is one tap away.
        rule.onAllNodesWithText("End now").assertCountEquals(0)
        rule.onNodeWithText("Timer only. Apps stay available on this phone.").assertIsDisplayed()
        openDetails()

        clock.advanceMinutes(25); tick(store)
        // Details are open and scrolled; the status line is above them.
        rule.onNodeWithText("On a break · round 1 of 2").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Focus resumes", substring = true).onFirst().assertExists()
        clock.advanceMinutes(5); tick(store)
        rule.onNodeWithText("Round 2 of 2").performScrollTo().assertIsDisplayed()
        clock.advanceMinutes(25); tick(store)
        rule.onNodeWithText("Session complete").assertIsDisplayed()
        rule.onNodeWithText("Focused 50 min").assertIsDisplayed()
        openSummary()
        rule.onNodeWithText("Finished as planned.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("You didn’t open any blocked apps or sites.").assertDoesNotExist()
        rule.onNodeWithText("Timer only: nothing was blocked on this phone.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        // Back to the idle lock.
        rule.onNodeWithTag("start-session").assertExists()
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
        rule.onNodeWithText("Usage access").assertDoesNotExist()
        rule.onNodeWithText("Can’t use Accessibility?").performScrollTo().performClick()
        rule.onNodeWithText("Usage access").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Display over other apps").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.onNodeWithText("No sessions planned yet").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Look and messages").assertIsDisplayed()
        rule.onNodeWithTag("settings-Look and messages").performClick()
        rule.onNodeWithText("Appearance").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back to Settings").performClick()
        rule.onNodeWithText("Account").assertIsDisplayed()
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
        rule.onNodeWithText("Edit apps and sites").performClick() // R64: summary first
        rule.onNodeWithText("Allow only these").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithContentDescription("Back to profiles").performClick() // Save returns to the summary
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
        rule.onNodeWithText("Turn on Accessibility", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("Use Accessibility for blocking?").assertIsDisplayed()
        rule.onNodeWithText("doesn’t read what you type", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        rule.onNodeWithText("Use Accessibility for blocking?").assertDoesNotExist()
    }

    @Test fun websitesTabValidatesAndAdds() {
        launch()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Edit apps and sites").performClick() // R64: summary first
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
        rule.onNodeWithContentDescription("Back to profiles").performClick() // Save returns to the summary
        rule.onNodeWithText("1 website blocked during focus").assertIsDisplayed()
    }

    @Test fun websitesAllowOnlyModeSaves() {
        launch()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Edit apps and sites").performClick() // R64: summary first
        rule.onNodeWithText("Websites").performClick()
        rule.onAllNodesWithText("Allow only these").onFirst().performClick()
        rule.onNodeWithText("No sites allowed yet", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Website to allow").performTextInput("wikipedia.org")
        rule.onNodeWithText("Add").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithContentDescription("Back to profiles").performClick() // Save returns to the summary
        rule.onNodeWithText("Only 1 website allowed during focus").assertIsDisplayed()
    }

    @Test fun profileSummaryListsAppsAndUpdatesAfterEdit() {
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val two = app.lockdin.enforce.InstalledApps.launchable(ctx).take(2)
        org.junit.Assume.assumeTrue("needs two launchable apps on the emulator", two.size == 2)
        val rules = app.lockdin.enforce.AppRules(packages = two.map { it.packageName }.toSet(), sites = setOf("youtube.com"))
        val s = AppState()
        launch(s.copy(profiles = s.profiles.map { if (it.id == "study") it.copy(rules = rules) else it }), home = true)
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Blocked apps · 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("${two[0].label}, blocked").performScrollTo().assertExists()
        rule.onNodeWithText("Websites · 1").performScrollTo().assertIsDisplayed()
        // The test emulator hasn't granted usage access, so the truthful pill is "not set up".
        rule.onNodeWithTag("profile-status").assertContentDescriptionEquals("Status: Blocking isn’t set up")
        shot("16-profile-summary-light")
        rule.onNodeWithTag("edit-profile").performClick()
        rule.onNodeWithContentDescription("Search apps").performTextInput(two[0].label)
        // The search field also contains the label; tap the app's row (the one that isn't a text field).
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(two[0].label) and hasSetTextAction().not()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText(two[0].label) and hasSetTextAction().not()).onFirst().performClick()
        rule.onNodeWithText("1 chosen").assertExists()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Blocked apps · 1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("${two[0].label}, blocked").assertDoesNotExist()
    }

    @Test fun profileSummaryDark() {
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val apps = app.lockdin.enforce.InstalledApps.launchable(ctx).take(4).map { it.packageName }.toSet()
        val rules = app.lockdin.enforce.AppRules(packages = apps, sites = setOf("youtube.com", "reddit.com"))
        val s = AppState()
        launch(s.copy(profiles = s.profiles.map { if (it.id == "study") it.copy(rules = rules) else it }), dark = true, home = true)
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Edit apps and sites").assertIsDisplayed()
        shot("17-profile-summary-dark")
    }

    @Test fun profileSummaryEmptyOffersAddApps() {
        launch(home = true)
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        if (rule.onAllNodesWithText("No apps blocked yet").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Add apps").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Search apps").assertExists()
        }
    }

    @Test fun newProfileOpensEditorAndCanBeDeleted() {
        launch()
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("New profile").performClick()
        rule.onNodeWithContentDescription("New profile name").performTextInput("Lab report")
        rule.onNodeWithText("Create").performClick()
        // The editor opens straight away, with the essentials note.
        // The essentials note now lives under "How blocking works" (polish pass).
        rule.onNodeWithText("How blocking works").assertIsDisplayed()
        rule.onNodeWithText("How blocking works").performClick()
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
        rule.onNodeWithTag("start-session").performClick()
        val before = app.lockdin.ui.StartIntro.plays
        rule.onNodeWithText("Start timer").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("plays once for the manual start", before + 1, app.lockdin.ui.StartIntro.plays)
        // Rotation / activity re-creation rebuilds the screen: no replay.
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("no replay after re-creation", before + 1, app.lockdin.ui.StartIntro.plays)
        rule.onNodeWithText("Ends at", substring = true).assertExists()
    }

    @Test fun startSequencePlaysOnEveryManualStart() {
        // Owner report: the dial turned only for the first session. Start, finish, start again
        // (Done → New session) and once more with "Same again".
        val store = launch(AppState(last = app.lockdin.state.LastChoice(25, 5, 1, "study")))
        val p0 = app.lockdin.ui.StartIntro.plays; val d0 = app.lockdin.ui.StartIntro.drawn
        rule.onNodeWithText("Start timer").performClick(); rule.waitForIdle()
        clock.advanceMinutes(26); tick(store)
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithTag("start-session").performClick()
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
        openSection("Starting a session")
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
        // The streak ring sits above the week and the list; scroll the list to reach them.
        rule.onNodeWithTag("history-list").performScrollToNode(hasText("This week"))
        rule.onNodeWithText("This week").assertIsDisplayed()
        rule.onNodeWithTag("history-list").performScrollToNode(hasText("Focus session"))
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
        rule.onNode(hasSetTextAction()).performImeAction() // closes the keyboard, as Done does
        rule.onNodeWithText("Save as preset").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Preset name").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Save").onLast().performClick()
        org.junit.Assert.assertEquals("Physics review", store.state.presets.single().name)
        rule.onNodeWithText("Back").performScrollTo().performClick()
        waitForText("Start a preset", 5_000)
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
        // The keyboard's Done adds the step (a tap on Add can land on the keyboard in the test activity).
        rule.onNodeWithContentDescription("New step").performImeAction()
        rule.waitForIdle()
        waitForText("Remove", 5_000)
        rule.onNodeWithText("Save").performClick()
        org.junit.Assert.assertEquals(listOf("Outline"), store.state.presets.single().steps)
        rule.onNodeWithText("Essay").performScrollTo().performClick()
        // The keyboard may still be closing; a touch sent mid-animation can be refused once.
        rule.waitForIdle()
        runCatching { rule.onNodeWithText("Delete").performClick() }.getOrElse { Thread.sleep(500); rule.waitForIdle(); rule.onNodeWithText("Delete").performClick() }
        rule.onAllNodesWithText("Delete").onLast().performClick()
        org.junit.Assert.assertTrue(store.state.presets.isEmpty())
    }

    @Test fun streakReachesGoalAndStartsAnUnlockedSession() {
        val store = launch(home = true)
        val day = 86_400_000L
        for (i in 1..30) hist.add(app.lockdin.engine.HistoryEntry("d$i", clock.now() - i * day, clock.now() - i * day + 1_500_000, clock.now() - i * day + 1_500_000,
            1_500_000, 1_500_000, 1, 1, "Read", "study", "Study", app.lockdin.engine.Outcome.COMPLETED), 365, clock.now())
        rule.onNodeWithTag("tab-history").performClick()
        rule.onNodeWithContentDescription("Focus streak: 30 of 30 days", substring = true).assertExists()
        shot("15-history-streak")
        rule.onNodeWithTag("history-list").performScrollToNode(hasText("Try an unlocked session"))
        rule.onNodeWithText("Try an unlocked session").performClick()
        // Both sit in the pinned area above the tab bar, which doesn't scroll.
        rule.onNodeWithText("Unlocked session").assertIsDisplayed()
        rule.onNodeWithText("Start unlocked").performClick()
        org.junit.Assert.assertTrue(store.state.active!!.unlocked)
        openDetails()
        rule.onNodeWithText("Unlocked session · nothing is blocked", substring = true).assertExists()
    }

    @Test fun historySettingsRetentionAndDeleteAll() {
        val store = launch(home = true)
        hist.add(app.lockdin.engine.HistoryEntry("e1", clock.now() - 3_600_000, clock.now() - 1_800_000, clock.now() - 1_800_000,
            1_800_000, 1_800_000, 1, 1, "Read", "study", "Study", app.lockdin.engine.Outcome.COMPLETED), 365, clock.now())
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("History and data")
        rule.onNodeWithText("Keep history").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1 session. Opens Android’s share sheet.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Delete all history").performScrollTo().performClick()
        rule.onAllNodesWithText("Delete").onLast().performClick()
        org.junit.Assert.assertTrue(hist.entries.isEmpty())
        org.junit.Assert.assertEquals(365, store.state.historyDays)
    }

    @Test fun profileAudioTabSavesBuiltInAudioAndMusicAccess() {
        val store = launch(home = true)
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Edit apps and sites").performClick() // R64: summary first
        rule.onNodeWithText("Audio").performClick()
        rule.onNodeWithText("Play study audio").performClick()
        rule.onNodeWithText("Strings").performScrollTo().performClick()
        rule.onNodeWithText("Development recordings", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Limited browsing").performScrollTo().performClick()
        rule.onNodeWithText("5 min (suggested)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Allowing an app allows everything in it", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()
        // Limited access with no app chosen isn't saved.
        rule.onNodeWithText("Choose at least one music app", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Follow blocking rules").performScrollTo().performClick()
        rule.onNodeWithText("Save").performClick()
        val a = store.state.profiles.first { it.id == "study" }.audio
        org.junit.Assert.assertTrue(a.builtIn)
        org.junit.Assert.assertEquals(app.lockdin.audio.AudioCollection.STRINGS, a.collection)
        org.junit.Assert.assertEquals(app.lockdin.audio.MusicAccess.NORMAL, a.access)
    }

    @Test fun sessionShowsCompactAudioControlAndAllowance() {
        val base = AppState(last = app.lockdin.state.LastChoice(25, 5, 1, "study"))
        val audio = app.lockdin.state.ProfileAudio(builtIn = true, collection = app.lockdin.audio.AudioCollection.RAIN,
            access = app.lockdin.audio.MusicAccess.LIMITED, musicApps = setOf("com.spotify.music"))
        launch(base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(audio = audio) else it }))
        openOptions()
        rule.onNodeWithText("Audio: Rain · music apps 5 min").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Start timer").performClick()
        rule.onNodeWithContentDescription("Play study audio").performScrollTo().assertExists()
        rule.onNodeWithText("Music apps: 5:00 of browsing left").performScrollTo().assertIsDisplayed()
        // Options stay folded away until asked for.
        rule.onNodeWithContentDescription("Study audio volume").assertDoesNotExist()
        rule.onNodeWithContentDescription("Show audio options").performClick()
        rule.onNodeWithContentDescription("Study audio volume").assertExists()
    }

    @Test fun scheduleOffersCalendarSuggestionsOnlyWhenAsked() {
        launch(home = true)
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.onNodeWithText("Find free time in your calendar").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("never changes your calendar", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("Calendar")
        rule.onNodeWithText("Suggest times from my calendar").performScrollTo().assertIsDisplayed()
    }

    @Test fun backgroundsAndQuotesAreOptionalAndSeparate() {
        val store = launch(home = true)
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("Look and background")
        // Bring the picker on screen first (the page scrolls vertically, the picker sideways).
        rule.onNodeWithText("Quotes").performScrollTo()
        rule.onNodeWithTag("bg-MOUNTAINS").performScrollTo().performClick()
        org.junit.Assert.assertEquals("MOUNTAINS", store.state.backdrop)
        org.junit.Assert.assertFalse(store.state.quotesOn)
        rule.onNodeWithText("Quotes").performScrollTo().performClick()
        org.junit.Assert.assertTrue(store.state.quotesOn)
        rule.onNodeWithTag("tab-focus").performClick()
        rule.onNodeWithTag("start-session").performClick()
        rule.onNodeWithText("Start timer").performClick()
        val line = app.lockdin.ui.Quotes.forSession(store.state.active!!.id)
        rule.onNodeWithText(line).assertIsDisplayed()
        // The lock and its spoken label are unchanged by the background.
        rule.onNodeWithContentDescription("Focus:", substring = true).assertExists()
        shot("14-focus-mountains-quote")
    }

    @Test fun ownMessagesShowDuringTheSession() {
        val store = launch(home = true)
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("Messages")
        rule.onNodeWithText("My own words").performScrollTo().performClick()
        rule.onNodeWithContentDescription("New message").performScrollTo().performTextInput("Problem set first, then rest.")
        rule.onAllNodesWithText("Add").onFirst().performClick()
        org.junit.Assert.assertEquals(listOf("Problem set first, then rest."), store.state.customMessages)
        rule.onNodeWithTag("tab-focus").performClick()
        rule.onNodeWithTag("start-session").performClick()
        rule.onNodeWithText("Start timer").performClick()
        openDetails()
        rule.onNodeWithText("Problem set first, then rest.").performScrollTo().assertIsDisplayed()
    }

    @Test fun questionTimerRecordsItemsAndShowsThemInTheSummary() {
        val store = launch(AppState(last = app.lockdin.state.LastChoice(25, 5, 1, "study")))
        rule.onNodeWithText("Start timer").performClick()
        rule.onNodeWithText("Time questions").performScrollTo().performClick()
        rule.onNodeWithText("Question 1").assertIsDisplayed()
        clock.advanceMinutes(3); tick(store)
        rule.onNodeWithText("Next question").performScrollTo().performClick()
        clock.advanceMinutes(2); tick(store)
        rule.onNodeWithText("Mark for review").performScrollTo().performClick()
        rule.onNodeWithText("Finish").performScrollTo().performClick()
        org.junit.Assert.assertEquals(3, app.lockdin.engine.Questions.durations(store.state.active!!.questions).size)
        clock.advanceMinutes(25); tick(store)
        rule.onNodeWithText("3 timed", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1 marked for review", substring = true).assertExists()
    }

    @Test fun checkInsAreOffByDefaultAndCanBeTurnedOn() {
        val store = launch(home = true)
        org.junit.Assert.assertEquals(0, store.state.checkInMinutes)
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("Check-ins and coaching")
        rule.onNodeWithText("Still working? check-in").performScrollTo().performClick()
        rule.onNodeWithText("After 15 min idle").performClick()
        org.junit.Assert.assertEquals(15, store.state.checkInMinutes)
    }

    @Test fun placeNeedsANameAndALocationAndCanBePaused() {
        val lib = app.lockdin.engine.Place("p1", "Library", 40.1, -88.2, profileId = "study")
        val store = launch(AppState(places = listOf(lib)), home = true)
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("Places")
        rule.onNodeWithText("Library").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Pause location routines").performScrollTo().performClick()
        org.junit.Assert.assertTrue(store.state.placesPaused)
        rule.onNodeWithText("Add a place").performScrollTo().performClick()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Give the place a name.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Name, e.g. Library").performTextInput("Café")
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Save your current location first.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Start automatically").performScrollTo().performClick()
        rule.onNodeWithText("Keep reminders").performClick()
        org.junit.Assert.assertEquals(1, store.state.places.size)
    }

    @Test fun setupMakesTheFirstProfileThenToursTheApp() {
        val store = launch(AppState(onboarded = false, profiles = emptyList()), home = true)
        // Why-slides, then "let's get started" with the account.
        rule.onNodeWithText("Start Lockd In. Keep distractions locked.").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Plan it, then just show up.").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("So let’s get started.").assertIsDisplayed()
        rule.onNodeWithText("Make my account").performClick()
        rule.onNodeWithText("Make your account").assertIsDisplayed()
        // Local checks run before anything is sent.
        rule.onAllNodesWithText("Create account").onLast().performScrollTo().performClick()
        rule.onNodeWithText("Add your first name.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Not now, use Lockd In on this phone only").performClick()
        rule.onNodeWithText("Block these apps during focus").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Continue").performClick()
        org.junit.Assert.assertEquals(listOf("Study"), store.state.profiles.map { it.name })
        rule.onNodeWithText("Turn on blocking").assertIsDisplayed()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Add a partner (optional)").assertIsDisplayed()
        // Polish pass: setup ends straight on Home, ready for the first session; the tour is in Help.
        rule.onNodeWithText("Skip and finish").performClick()
        org.junit.Assert.assertTrue(store.state.onboarded)
        org.junit.Assert.assertEquals(-1, store.state.tourStep)
        rule.onNodeWithText("Start your first session").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("Help and tour")
        rule.onNodeWithText("Replay the tour").performClick()
        // The tour points at parts of the home screen, one small bubble at a time.
        rule.onNodeWithText("1 of 6").assertIsDisplayed()
        rule.onNodeWithText("Start here").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("What’s next").assertIsDisplayed()
        rule.onNodeWithText("Skip tour").performClick()
        rule.onNodeWithText("What’s next").assertDoesNotExist()
        org.junit.Assert.assertEquals(-1, store.state.tourStep)
    }

    @Test fun setupCanBeEndedFromAnyStep() {
        val store = launch(AppState(onboarded = false, profiles = emptyList()), home = true)
        rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithText("Make my account").performClick()
        rule.onNodeWithText("End tutorial").performClick()
        org.junit.Assert.assertTrue(store.state.onboarded)
        org.junit.Assert.assertEquals(-1, store.state.tourStep)
        rule.onNodeWithTag("start-session").assertIsDisplayed()
    }

    @Test fun signInFormChecksEmailAndPassword() {
        launch(home = true)
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("Account")
        rule.onNodeWithText("Sign in").performClick()
        rule.onAllNodesWithText("Sign in").onLast().performScrollTo().performClick()
        rule.onNodeWithText("That email doesn’t look right.").performScrollTo().assertIsDisplayed()
    }

    @Test fun signedInAccountShowsInSettings() {
        launch(AppState(account = app.lockdin.state.Account("user_test_0001", "tray@example.com", "Tray")), home = true)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("tray@example.com").assertIsDisplayed()
        openSection("Account")
        rule.onNodeWithText("Sign out").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Delete account").performScrollTo().assertIsDisplayed()
    }

    @Test fun partnerDetailsShowOnlyWhenPartnerIsChosen() {
        val store = launch(AppState(commitment = app.lockdin.enforce.Commitment.LOCKED), home = true)
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("Ending early")
        rule.onNodeWithText("Your partner").assertDoesNotExist()
        rule.onNodeWithText("Partner’s first name").assertDoesNotExist()
        rule.onNodeWithText("Partner: someone you trust approves").performScrollTo().performClick()
        org.junit.Assert.assertEquals(app.lockdin.enforce.Commitment.PARTNER, store.state.commitment)
        rule.onNodeWithText("Your partner").performScrollTo().assertIsDisplayed()
    }

    @Test fun partnerSessionOffersCodeAndEmergencyWait() {
        val now = clock.now()
        val p = app.lockdin.state.Partner("Sam", "acct_test_0001", "link_test_0001", claimed = true)
        val store = launch(AppState(partner = p, commitment = app.lockdin.enforce.Commitment.PARTNER), home = true)
        rule.onNodeWithTag("start-session").performClick()
        openOptions()
        rule.onNodeWithText("Ending early:", substring = true).performScrollTo().assertIsDisplayed()
        store.update { s -> s.copy(active = app.lockdin.state.ActiveSession(
            "sess_partner_1", now, listOf(app.lockdin.engine.Segment(app.lockdin.engine.PhaseKind.FOCUS, 60)), "Essay",
            s.profiles.first().id, app.lockdin.engine.SessionControls(), commitment = app.lockdin.enforce.Commitment.PARTNER)) }
        waitForText("Details and options", 15_000)
        openDetails()
        waitForText("Partner session", 15_000)
        rule.onNodeWithText("Partner session", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("End early").performScrollTo().performClick()
        rule.onNodeWithText("Ask Sam").assertIsDisplayed()
        rule.onNodeWithText("Sam is with you? Use their code").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Partner’s code").performTextInput("000000")
        rule.onNodeWithText("End with the code").performScrollTo().performClick()
        rule.onNodeWithText("This phone doesn’t have the partner key", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Emergency end without Sam").performScrollTo().performClick()
        rule.onNodeWithText("Start the wait").performClick()
        val at = store.state.active!!.controls.emergencyRecoveryAt!!
        org.junit.Assert.assertTrue("wait ends at ${at - now} ms", at > now + 40 * 60_000L && at <= clock.now() + 45 * 60_000L)
        rule.onNodeWithText("Calls and messages are unlocked", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Cancel and keep focusing").performScrollTo().performClick()
        org.junit.Assert.assertNull(store.state.active!!.controls.emergencyRecoveryAt)
    }

    @Test fun privacyListsPermissionsAndDefaultsAreEditable() {
        val store = launch(home = true)
        rule.onNodeWithContentDescription("Settings").performClick()
        openSection("History and data")
        rule.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Only two things use the internet", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Read-only, for free-time suggestions").performScrollTo().assertIsDisplayed()
        openSection("New session defaults")
        rule.onNodeWithText("Focus length").performScrollTo().performClick()
        rule.onNodeWithText("25 min").performClick()
        org.junit.Assert.assertEquals(25, store.state.last.focusMinutes)
    }

    @Test fun screenshotBackgroundScenes() {
        // One running session per background, dark (as designed) and the words scene in light.
        val scenes = listOf("LIBRARY" to true, "MOUNTAINS" to true, "WORDS" to true, "LIBRARY" to false, "WORDS" to false)
        val dark = androidx.compose.runtime.mutableStateOf(true)
        val store = Store(AppState(last = app.lockdin.state.LastChoice(25, 5, 1, "study"), quotesOn = true)) {}
        rule.setContent { LockdInApp(store, clock, zone, dark.value, history = hist) }
        store.startSession(clock.now(), app.lockdin.engine.Session.roundsPlan(1, 25, 5), "Problem set 4", "study", store.state.last)
        scenes.forEachIndexed { i, (b, d) ->
            dark.value = d
            store.update { it.copy(backdrop = b, theme = if (d) app.lockdin.state.ThemeChoice.DARK else app.lockdin.state.ThemeChoice.LIGHT) }
            rule.waitForIdle()
            shot("3$i-bg-${b.lowercase()}-${if (d) "dark" else "light"}")
        }
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
        rule.onNodeWithText("Add session").performScrollTo().performClick()
        rule.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Physics review")
        // The software keyboard can cover this summary on the test device; checking it exists is enough here.
        rule.onNodeWithText("every week at", substring = true).assertExists()
        rule.onNodeWithText("Save session").performClick()
        rule.onNodeWithText("Every week").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Physics review").onFirst().assertIsDisplayed()
        rule.onNodeWithContentDescription("Physics review on").performScrollTo().performClick()
        rule.onNodeWithText("Off").performScrollTo().assertIsDisplayed()
        // The plan shows on the chosen day and in "Your plans"; edit from the list.
        rule.onAllNodesWithText("Physics review").onLast().performScrollTo().performClick()
        rule.onNodeWithText("Edit session").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        rule.onAllNodesWithText("Delete").onLast().performClick()
        rule.onNodeWithText("No sessions planned yet").performScrollTo().assertIsDisplayed()
    }

    // ---- Screenshots for design review (pulled by CI; not assertions) ----
    private fun shot(name: String) {
        rule.waitForIdle()
        // The software-rendered emulator sometimes misses the redraw deadline once; try again.
        val bmp = runCatching { rule.onRoot().captureToImage() }.getOrElse { rule.waitForIdle(); rule.onRoot().captureToImage() }.asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- Targeted fixes (owner 2026-11-10) ----
    @Test fun lengthPresetAndCustomAreExclusive() {
        launch()
        fun ends(min: Int) = "Ends at ${app.lockdin.ui.Fmt.clock(clock.now() + min * 60_000L, zone)}"
        rule.onNodeWithContentDescription("40 minutes").assertIsSelected()
        rule.onNodeWithText(ends(40), substring = true).assertExists()
        // Custom selects itself and opens the stepper; the preset lets go.
        rule.onNodeWithContentDescription("Custom minutes").performClick()
        rule.onNodeWithContentDescription("Custom minutes").assertIsSelected()
        rule.onNodeWithContentDescription("40 minutes").assertIsNotSelected()
        repeat(4) { rule.onNodeWithContentDescription("5 minutes longer").performScrollTo().performClick() }
        // At 60 the stepper's value equals a preset, but Custom stays the one selection.
        rule.onNodeWithContentDescription("Custom minutes").assertIsSelected()
        rule.onNodeWithContentDescription("60 minutes").assertIsNotSelected()
        rule.onNodeWithText(ends(60), substring = true).assertExists()
        shot("60-length-custom-at-60")
        // A preset takes the selection back and closes the stepper.
        rule.onNodeWithContentDescription("25 minutes").performClick()
        rule.onNodeWithContentDescription("25 minutes").assertIsSelected()
        rule.onNodeWithContentDescription("Custom minutes").assertIsNotSelected()
        rule.onAllNodesWithContentDescription("5 minutes longer").assertCountEquals(0)
        rule.onNodeWithText(ends(25), substring = true).assertExists()
        // And back: Custom at a preset value (25) is still Custom.
        rule.onNodeWithContentDescription("Custom minutes").performClick()
        rule.onNodeWithContentDescription("Custom minutes").assertIsSelected()
        rule.onNodeWithContentDescription("25 minutes").assertIsNotSelected()
        rule.onNodeWithContentDescription("5 minutes longer").performScrollTo().performClick()
        rule.onNodeWithText(ends(30), substring = true).assertExists()
        shot("61-length-preset-after-custom")
    }

    private fun musicPicker(dark: Boolean, name: String) {
        launch(AppState(theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT), dark = dark)
        openOptions()
        rule.onNodeWithText("Choose music").performScrollTo().performClick()
        rule.onNodeWithText("Strings").performClick()
        rule.onNodeWithText("Strings").assertIsSelected()
        rule.onNodeWithContentDescription("Preview Strings").assertExists()
        rule.onNodeWithText("Done").assertIsDisplayed()
        shot(name)
    }
    @Test fun musicPickerLight() = musicPicker(false, "62-music-picker-light")
    @Test fun musicPickerDark() = musicPicker(true, "63-music-picker-dark")

    private fun websitesTab(dark: Boolean, name: String) {
        launch(AppState(theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT), dark = dark, home = true)
        rule.onNodeWithTag("tab-profiles").performClick()
        rule.onNodeWithText("Study").performClick()
        rule.onNodeWithText("Edit apps and sites").performClick()
        rule.onNodeWithText("Websites").performClick()
        for (d in listOf("youtube.com", "reddit.com", "instagram.com")) {
            rule.onNodeWithContentDescription("Website to block").performTextInput(d)
            rule.onNodeWithText("Add").performClick()
        }
        rule.onAllNodesWithText("youtube.com").onFirst().assertExists()
        rule.onNodeWithText("How website blocking works").assertExists()
        shot(name)
    }
    @Test fun websitesTabLight() = websitesTab(false, "64-websites-light")
    @Test fun websitesTabDark() = websitesTab(true, "65-websites-dark")

    private fun streak(days: Int, dark: Boolean, name: String) {
        launch(AppState(theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT), dark = dark, home = true)
        val day = 86_400_000L
        for (i in 1..days) hist.add(app.lockdin.engine.HistoryEntry("d$i", clock.now() - i * day, clock.now() - i * day + 1_500_000, clock.now() - i * day + 1_500_000,
            1_500_000, 1_500_000, 1, 1, "Read", "study", "Study", app.lockdin.engine.Outcome.COMPLETED), 365, clock.now())
        if (days == 0) hist.add(app.lockdin.engine.HistoryEntry("x", clock.now() - day, clock.now() - day + 600_000, clock.now() - day + 600_000,
            600_000, 0, 1, 0, null, "study", "Study", app.lockdin.engine.Outcome.ENDED_EARLY), 365, clock.now())
        rule.onNodeWithTag("tab-history").performClick()
        rule.onNodeWithContentDescription("Focus streak: $days of 30 days", substring = true).assertExists()
        shot(name)
    }
    @Test fun streakHalfLight() = streak(15, false, "66-streak-15-light")
    @Test fun streakHalfDark() = streak(15, true, "67-streak-15-dark")
    @Test fun streakZeroLight() = streak(0, false, "68-streak-0-light")
    @Test fun streakZeroDark() = streak(0, true, "69-streak-0-dark")

    // ---- Device shapes (polish pass, 2026-11-09): Galaxy Z Flip (tall, narrow) and a short phone. ----
    private val flip = DpSize(407.dp, 994.dp)
    private val short = DpSize(360.dp, 640.dp)
    private val longEmail = app.lockdin.state.Account("u1", "trayvonn.foster.longer.address.2024@university-example.edu", "Tray")
    private val longTask = "TAM 212 · Rigid body dynamics practice problems, chapters 14 through 16"

    @Test fun shapeHome() {
        launch(AppState(account = longEmail), home = true, viewport = flip); vshot("40-flip-home-light")
    }
    @Test fun shapeHomeLibrary() {
        launch(AppState(account = longEmail, backdrop = "LIBRARY", theme = ThemeChoice.DARK), dark = true, home = true, viewport = flip); vshot("41-flip-home-library-dark")
    }
    @Test fun shapeHomeLibraryLight() {
        launch(AppState(account = longEmail, backdrop = "LIBRARY"), home = true, viewport = flip); vshot("42-flip-home-library-light")
    }
    @Test fun shapeSetup() {
        launch(AppState(account = longEmail), viewport = flip); vshot("43-flip-setup-light")
        rule.onNodeWithContentDescription("What are you working on?").performTextInput(longTask)
        vshot("44-flip-setup-keyboard")
    }
    @Test fun shapeSetupLibraryDark() {
        launch(AppState(account = longEmail, backdrop = "LIBRARY", theme = ThemeChoice.DARK), dark = true, viewport = flip); vshot("45-flip-setup-library-dark")
    }
    @Test fun shapeSettings() {
        launch(AppState(account = longEmail), home = true, viewport = flip)
        rule.onNodeWithContentDescription("Settings").performClick()
        vshot("46-flip-settings")
        rule.onNodeWithTag("viewport").performTouchInput { swipeUp() }
        vshot("47-flip-settings-scrolled")
    }
    @Test fun shapeSettingsLargeText() {
        launch(AppState(account = longEmail, theme = ThemeChoice.DARK), dark = true, fontScale = 1.5f, home = true, viewport = flip)
        rule.onNodeWithContentDescription("Settings").performClick()
        vshot("48-flip-settings-font150-dark")
    }
    @Test fun shapeShortHomeAndSetup() {
        launch(AppState(account = longEmail, theme = ThemeChoice.DARK), dark = true, home = true, viewport = short); vshot("50-short-home-dark")
        rule.onNodeWithTag("start-session").performClick()
        vshot("51-short-setup-dark")
    }
    @Test fun shapeShortSettings() {
        launch(AppState(account = longEmail), home = true, viewport = short)
        rule.onNodeWithContentDescription("Settings").performClick()
        vshot("52-short-settings-light")
    }
    @Test fun shapeFocus() {
        val store = launch(AppState(account = longEmail), viewport = flip)
        rule.onNodeWithContentDescription("What are you working on?").performTextInput(longTask)
        rule.onNodeWithText("Start timer").performClick()
        clock.advanceMinutes(7); tick(store)
        vshot("49-flip-focus-light")
    }
    @Test fun shapeStreakZero() {
        launch(home = true, viewport = flip)
        hist.add(app.lockdin.engine.HistoryEntry("x", clock.now() - 86_400_000, clock.now() - 86_400_000 + 600_000, clock.now() - 86_400_000 + 600_000,
            600_000, 0, 1, 0, null, "study", "Study", app.lockdin.engine.Outcome.ENDED_EARLY), 365, clock.now())
        rule.onNodeWithTag("tab-history").performClick()
        vshot("53-flip-streak-zero")
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
        openOptions()
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
        rule.onNodeWithText("Add session").performScrollTo().performClick()
        rule.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Physics review")
        shot("08-schedule-editor")
        rule.onNodeWithText("Save session").performClick()
        shot("09-schedule-list")
    }
    @Test fun idleHomeShowsTimeAndNextSession() {
        val start = java.time.LocalTime.of(19, 0)
        launch(AppState(schedule = listOf(app.lockdin.engine.ScheduledSession("p", "Physics review", java.time.DayOfWeek.entries.toSet(), start, 40, 10, 2, "study"))), home = true)
        rule.onNodeWithContentDescription("It's 6:00", substring = true).assertIsDisplayed()
        // Greeting under the lock (R54): "Good evening" and "Physics review in 1 h."
        rule.onNodeWithText("Good evening").assertIsDisplayed()
        rule.onNodeWithText("Physics review in 1 h.").assertIsDisplayed()
        rule.onNodeWithTag("start-session").performClick()
        rule.onNodeWithText("Start timer").assertExists()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithTag("start-session").assertExists()
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
        rule.onNodeWithTag("start-session").assertIsDisplayed()
    }
    @Test fun autoStartIsExplainedBeforeItTurnsOn() {
        launch()
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.onNodeWithText("Add session").performScrollTo().performClick()
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
        openSection("Notifications")
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
        openSection("Notifications")
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
