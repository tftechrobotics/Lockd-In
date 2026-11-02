package app.lockdin

import app.lockdin.audio.Ambient
import app.lockdin.audio.AmbientSynth
import app.lockdin.audio.AudioPlan
import app.lockdin.audio.Music
import app.lockdin.enforce.AppRules
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
import kotlin.math.abs

class AudioTest {
    @Test fun everyAmbientSoundIsAudibleBoundedAndDeterministic() {
        for (k in Ambient.values().filter { it != Ambient.NONE }) {
            val a = ShortArray(22_050 * 3); AmbientSynth(k, seed = 7).fill(a)
            val b = ShortArray(22_050 * 3); AmbientSynth(k, seed = 7).fill(b)
            assertTrue(k.name, a.contentEquals(b))
            val rms = kotlin.math.sqrt(a.map { it.toDouble() * it }.average())
            assertTrue("${k.name} rms=$rms", rms > 300)            // audible
            assertTrue(k.name, a.all { abs(it.toInt()) < 31_200 })  // never clips
        }
        val silent = ShortArray(1000); AmbientSynth(Ambient.NONE).fill(silent)
        assertTrue(silent.all { it.toInt() == 0 })
    }

    @Test fun recognisesPlaylistLinks() {
        assertEquals(Music.SPOTIFY, Music.providerOf("https://open.spotify.com/playlist/37i9dQZF1DX8NTLI2TtZa6?si=x"))
        assertEquals(Music.SPOTIFY, Music.providerOf("spotify:playlist:37i9dQZF1DX8NTLI2TtZa6"))
        assertEquals(Music.YOUTUBE_MUSIC, Music.providerOf("https://music.youtube.com/playlist?list=PL123"))
        assertEquals(Music.APPLE_MUSIC, Music.providerOf("https://music.apple.com/us/playlist/study/pl.abc"))
        assertEquals(Music.SOUNDCLOUD, Music.providerOf("https://soundcloud.com/user/sets/focus"))
        assertNull(Music.providerOf("https://www.youtube.com/watch?v=x"))
        assertNull(Music.providerOf("https://open.spotify.com.evil.example/playlist/x"))
        assertNull(Music.providerOf("not a link"))
        assertEquals("https://soundcloud.com/a/sets/b", Music.clean(" http://soundcloud.com/a/sets/b "))
    }

    @Test fun ambientFollowsFocusBreaksAndThePersonsChoice() {
        val a = ProfileAudio(ambient = Ambient.RAIN)
        assertTrue(AudioPlan.ambientWanted(a, "s", inFocus = true, inBreak = false, pausedFor = null, startedFor = null))
        assertFalse(AudioPlan.ambientWanted(a, "s", false, true, null, null))                  // paused for breaks
        assertTrue(AudioPlan.ambientWanted(a.copy(duringBreaks = true), "s", false, true, null, null))
        assertFalse(AudioPlan.ambientWanted(a, "s", true, false, "s", null))                   // person paused
        assertTrue(AudioPlan.ambientWanted(a, "s2", true, false, "s", null))                   // a new session starts fresh
        assertFalse(AudioPlan.ambientWanted(a.copy(autoPlay = false), "s", true, false, null, null))
        assertTrue(AudioPlan.ambientWanted(a.copy(autoPlay = false), "s", true, false, null, "s"))
        assertFalse(AudioPlan.ambientWanted(ProfileAudio(), "s", true, false, null, null))
        assertFalse(AudioPlan.ambientWanted(a, "s", false, false, null, null))                 // session over
    }

    @Test fun theProfilesMusicAppIsNeverBlockedInItsSessions() {
        val spotify = Music.SPOTIFY.pkg
        val audio = ProfileAudio(musicLink = "https://open.spotify.com/playlist/x")
        fun started(rules: AppRules): AppRules {
            val base = AppState()
            val store = Store(base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(rules = rules, audio = audio) else it })) {}
            store.startSession(1_793_300_000_000, Session.roundsPlan(1, 25, 5), null, "study", LastChoice(25, 5, 1, "study"))
            return store.state.active!!.rules
        }
        assertFalse(spotify in started(AppRules(RuleMode.BLOCK_LIST, setOf(spotify, "com.x"))).packages)
        assertTrue(spotify in started(AppRules(RuleMode.APPROVED_ONLY, setOf("com.notes"))).packages)
    }

    @Test fun audioSettingsRoundTripAndBadLinksDrop() {
        val base = AppState()
        val audio = ProfileAudio(Ambient.WAVES, 0.3f, "https://music.youtube.com/playlist?list=PL1", autoPlay = false, duringBreaks = true)
        val s = base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(audio = audio) else it },
            playlists = listOf(app.lockdin.state.SavedPlaylist("Lofi", "https://music.youtube.com/playlist?list=PL1")))
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
        val raw = StateCodec.encode(s).replace("https://music.youtube.com/playlist?list=PL1", "https://evil.example/x").replace("\"WAVES\"", "\"NOPE\"")
        val d = StateCodec.decode(raw).profiles.first { it.id == "study" }.audio
        assertNull(d.musicLink); assertEquals(Ambient.NONE, d.ambient)
    }

    @Test fun playUrisAndOpenLinks() {
        assertEquals("spotify:playlist:37i9dQZF1DX8NTLI2TtZa6", Music.playUri("https://open.spotify.com/playlist/37i9dQZF1DX8NTLI2TtZa6?si=abc"))
        assertEquals("spotify:album:4aawyAB9vmqN3uQ7FjRGTy", Music.playUri("https://open.spotify.com/intl-de/album/4aawyAB9vmqN3uQ7FjRGTy"))
        assertEquals("https://music.youtube.com/playlist?list=PL1", Music.playUri("https://music.youtube.com/playlist?list=PL1"))
        assertEquals("https://music.youtube.com/watch?list=PL1", Music.openUri("https://music.youtube.com/playlist?list=PL1&si=x"))
        assertEquals("https://soundcloud.com/a/sets/b", Music.openUri("https://soundcloud.com/a/sets/b"))
        assertEquals("Spotify playlist", Music.defaultName("https://open.spotify.com/playlist/x"))
    }

    // CI only (needs org.json): saved playlists round-trip, and an older profile link is kept as a saved playlist.
    @Test fun savedPlaylistsRoundTripAndMigrate() {
        val s = AppState(playlists = listOf(app.lockdin.state.SavedPlaylist("Deep work", "https://open.spotify.com/playlist/A")))
        assertEquals(s.playlists, StateCodec.decode(StateCodec.encode(s)).playlists)
        val old = AppState(profiles = AppState().profiles.map { if (it.id == "study") it.copy(audio = ProfileAudio(musicLink = "https://music.youtube.com/playlist?list=PL9")) else it })
        val raw = StateCodec.encode(old).replace("\"playlists\":[]", "\"playlists\":null")
        val d = StateCodec.decode(raw).playlists
        assertEquals(listOf("https://music.youtube.com/playlist?list=PL9"), d.map { it.link })
        assertEquals("YouTube Music playlist", d.single().name)
    }
}
