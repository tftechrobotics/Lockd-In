package app.lockdin

import app.lockdin.audio.AudioCollection
import app.lockdin.audio.AudioRules
import app.lockdin.audio.BreakAudio
import app.lockdin.audio.MusicAccess
import app.lockdin.audio.MusicAllowance
import app.lockdin.enforce.AppRules
import app.lockdin.enforce.BlockPolicy
import app.lockdin.enforce.RuleMode
import app.lockdin.engine.Session
import app.lockdin.state.AppState
import app.lockdin.state.LastChoice
import app.lockdin.state.ProfileAudio
import app.lockdin.state.StateCodec
import app.lockdin.state.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Study audio and the music browsing allowance (R48). */
class AudioTest {
    private val spotify = "com.spotify.music"
    private val ytm = "com.google.android.apps.youtube.music"
    private val limited = ProfileAudio(access = MusicAccess.LIMITED, musicApps = setOf(spotify, ytm), allowanceMin = 5)

    @Test fun collectionsAreSmallCuratedAndInstrumental() {
        // Study music and quieter, chill sounds (owner 2026-11-02); each one small and curated.
        assertTrue(AudioCollection.values().map { it.label }.containsAll(listOf("Piano", "Strings", "Lo-fi", "Ambient", "Quiet piano", "Rain", "Ocean", "Forest", "Brown noise")))
        assertTrue(AudioCollection.values().any { it.group == app.lockdin.audio.AudioGroup.STUDY } && AudioCollection.values().any { it.group == app.lockdin.audio.AudioGroup.CHILL })
        AudioCollection.values().forEach { assertTrue(it.tracks.isNotEmpty() && it.tracks.size <= 5) }
        AudioCollection.values().filter { it.loop }.forEach { assertEquals(1, it.tracks.size) }
        assertTrue(AudioCollection.RAIN.loop)
        assertFalse(AudioCollection.PIANO.loop)
    }

    @Test fun builtInAudioIsOffByDefaultAndIndependentOfMusicAccess() {
        val d = ProfileAudio()
        assertFalse(d.builtIn); assertFalse(d.playOnStart); assertEquals(MusicAccess.NORMAL, d.access)
        assertEquals(5, d.allowanceMin)
        // Either setting works without the other.
        assertTrue(ProfileAudio(builtIn = true).accessApps.isEmpty())
        assertEquals(setOf(spotify, ytm), limited.accessApps)
        assertTrue(ProfileAudio(access = MusicAccess.NORMAL, musicApps = setOf(spotify)).accessApps.isEmpty())
    }

    @Test fun onlyForegroundFocusTimeCounts() {
        val all = MusicAllowance.allowanceMs(limited)
        var used = 0L
        // One second in front during focus counts.
        used = MusicAllowance.step(used, 1_000, counting = true, allowanceMs = all); assertEquals(1_000, used)
        // Background playback, screen off, other apps, breaks: not counting.
        repeat(600) { used = MusicAllowance.step(used, 1_000, counting = false, allowanceMs = all) }
        assertEquals(1_000, used)
    }

    @Test fun aLongGapNeverAddsMoreThanOneStep() {
        // A missed check (or a wall-clock jump, which the service never feeds in) can't spend the allowance at once.
        assertEquals(MusicAllowance.MAX_STEP_MS, MusicAllowance.step(0, 3_600_000, counting = true, allowanceMs = 300_000))
        assertEquals(0, MusicAllowance.step(0, -5_000, counting = true, allowanceMs = 300_000))
    }

    @Test fun sharedAcrossAppsAndNeverAboveTheAllowance() {
        val all = MusicAllowance.allowanceMs(limited)
        var used = 0L
        // Switching apps doesn't reset or double anything: it is one number per session.
        repeat(200) { used = MusicAllowance.step(used, 1_000, true, all) }   // Spotify
        repeat(200) { used = MusicAllowance.step(used, 1_000, true, all) }   // YouTube Music
        assertEquals(all, used)
        assertTrue(MusicAllowance.spent(limited, used))
        assertEquals(0, MusicAllowance.remainingMs(limited, used))
    }

    @Test fun oneWarningAtThirtySeconds() {
        val all = MusicAllowance.allowanceMs(limited)
        assertFalse(MusicAllowance.shouldWarn(limited, all - 31_000, warned = false))
        assertTrue(MusicAllowance.shouldWarn(limited, all - 30_000, warned = false))
        assertFalse(MusicAllowance.shouldWarn(limited, all - 10_000, warned = true))
        assertFalse(MusicAllowance.shouldWarn(limited, all, warned = false))       // already spent: no warning
        assertFalse(MusicAllowance.shouldWarn(ProfileAudio(), 0, warned = false))   // not limited
    }

    @Test fun accessDecisions() {
        val all = MusicAllowance.allowanceMs(limited)
        assertEquals(false, MusicAllowance.decide(spotify, limited, 0))
        assertEquals(true, MusicAllowance.decide(spotify, limited, all))
        assertNull(MusicAllowance.decide("com.instagram.android", limited, all))  // not chosen: normal rules
        assertEquals(false, MusicAllowance.decide(spotify, limited.copy(access = MusicAccess.ALWAYS), all))
        assertNull(MusicAllowance.decide(spotify, limited.copy(access = MusicAccess.NORMAL), 0))
    }

    private fun started(rules: AppRules, audio: ProfileAudio): app.lockdin.state.ActiveSession {
        val base = AppState()
        val store = Store(base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(rules = rules, audio = audio) else it })) {}
        store.startSession(1_793_300_000_000, Session.roundsPlan(1, 25, 5), null, "study", LastChoice(25, 5, 1, "study"))
        return store.state.active!!
    }

    @Test fun limitedAppsAreAllowedUntilSpentThenCovered() {
        // Block list that doesn't list Spotify, approved-only that does: LIMITED still limits it.
        for (rules in listOf(AppRules(RuleMode.BLOCK_LIST, setOf("com.x")), AppRules(RuleMode.APPROVED_ONLY, setOf(spotify, "com.notes")))) {
            val s = started(rules, limited)
            assertTrue(s.rules.restrictsAnything)
            assertFalse(BlockPolicy.isRestrictedIn(spotify, s, emptySet()))
            assertTrue(BlockPolicy.isRestrictedIn(spotify, s.copy(musicUsedMs = MusicAllowance.allowanceMs(limited)), emptySet()))
        }
    }

    @Test fun alwaysAllowedAppsAreNeverCoveredAndNormalChangesNothing() {
        val always = limited.copy(access = MusicAccess.ALWAYS)
        val s = started(AppRules(RuleMode.BLOCK_LIST, setOf(spotify, "com.x")), always)
        assertFalse(BlockPolicy.isRestrictedIn(spotify, s, emptySet()))
        assertTrue(BlockPolicy.isRestrictedIn("com.x", s, emptySet()))
        val n = started(AppRules(RuleMode.BLOCK_LIST, setOf(spotify)), limited.copy(access = MusicAccess.NORMAL))
        assertTrue(BlockPolicy.isRestrictedIn(spotify, n, emptySet()))
    }

    @Test fun setupChoiceAppliesToThisSessionOnly() {
        val base = AppState()
        val store = Store(base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(audio = limited) else it })) {}
        store.startSession(1_793_300_000_000, Session.roundsPlan(1, 25, 5), null, "study", LastChoice(25, 5, 1, "study"),
            audio = limited.copy(allowanceMin = 10, builtIn = true, collection = AudioCollection.STRINGS))
        assertEquals(10, store.state.active!!.audio.allowanceMin)
        assertEquals(5, store.state.profiles.first { it.id == "study" }.audio.allowanceMin)
    }

    @Test fun musicUseNeverGoesBackwards() {
        val base = AppState()
        val store = Store(base) {}
        store.startSession(1_793_300_000_000, Session.roundsPlan(1, 25, 5), null, "study", LastChoice(25, 5, 1, "study"))
        val id = store.state.active!!.id
        store.setMusicUse(id, 60_000)
        store.setMusicUse(id, 10_000)
        assertEquals(60_000, store.state.active!!.musicUsedMs)
        store.setMusicUse("other", 999_000)
        assertEquals(60_000, store.state.active!!.musicUsedMs)
    }

    @Test fun audioStartsOnlyForAFreshFocusPeriod() {
        val a = ProfileAudio(builtIn = true, playOnStart = true)
        val t = 1_793_300_000_000
        assertTrue(AudioRules.autoStart(a, t, t + 2_000, alreadyFor = false))
        assertFalse(AudioRules.autoStart(a, t, t + 30 * 60_000, alreadyFor = false))  // restart after reboot/recovery
        assertFalse(AudioRules.autoStart(a.copy(playOnStart = false), t, t, alreadyFor = false))
        assertFalse(AudioRules.autoStart(a.copy(builtIn = false), t, t, alreadyFor = false))
        assertFalse(AudioRules.autoStart(a, t, t, alreadyFor = true))
        // After a break: back on only if it was playing and the profile pauses for breaks.
        assertTrue(AudioRules.resumeAfterBreak(a.copy(playOnStart = false), true, t, t + 1_000))
        assertFalse(AudioRules.resumeAfterBreak(a, false, t, t + 1_000))
        assertFalse(AudioRules.resumeAfterBreak(a.copy(breaks = BreakAudio.CONTINUE), true, t, t + 1_000))
    }

    @Test fun formatRemaining() {
        assertEquals("5:00", MusicAllowance.format(300_000))
        assertEquals("0:01", MusicAllowance.format(1))
        assertEquals("0:00", MusicAllowance.format(0))
    }

    // CI only (needs org.json).
    @Test fun audioRoundTripsAndOldSettingsMigrate() {
        val base = AppState()
        val audio = ProfileAudio(true, AudioCollection.STRINGS, 0.3f, true, BreakAudio.CONTINUE, MusicAccess.LIMITED, setOf(spotify), 10)
        val s = base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(audio = audio) else it })
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
        // A B2 save: generated ambient sound and a playlist link.
        val old = StateCodec.encode(base).replace(
            Regex("\"audio\":\\{[^}]*\\}"),
            "\"audio\":{\"ambient\":\"WAVES\",\"level\":0.4,\"musicLink\":\"https://open.spotify.com/playlist/x\",\"autoPlay\":true,\"duringBreaks\":true}",
        )
        val d = StateCodec.decode(old).profiles.first().audio
        assertTrue(d.builtIn); assertEquals(AudioCollection.RAIN, d.collection); assertTrue(d.playOnStart)
        assertEquals(BreakAudio.CONTINUE, d.breaks); assertEquals(MusicAccess.NORMAL, d.access)
        // Junk reads as off.
        val junk = StateCodec.encode(s).replace("\"STRINGS\"", "\"NOPE\"").replace("\"LIMITED\"", "\"???\"")
        val j = StateCodec.decode(junk).profiles.first { it.id == "study" }.audio
        assertEquals(AudioCollection.PIANO, j.collection); assertEquals(MusicAccess.NORMAL, j.access)
    }

    @Test fun allowanceSurvivesARestart() {
        val base = AppState()
        val store = Store(base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(audio = limited) else it })) {}
        store.startSession(1_793_300_000_000, Session.roundsPlan(1, 25, 5), null, "study", LastChoice(25, 5, 1, "study"))
        store.setMusicUse(store.state.active!!.id, 123_000, warned = true)
        val back = StateCodec.decode(StateCodec.encode(store.state)).active!!
        assertEquals(123_000, back.musicUsedMs); assertTrue(back.musicWarned)
    }
}
