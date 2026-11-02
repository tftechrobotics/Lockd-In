// Built-in study audio player (R48). Lockd In plays its own bundled recordings with Media3
// ExoPlayer. During a session the player lives in the blocking service (FocusService), which is
// already a foreground service of type mediaPlayback, so it keeps playing with the screen off
// and while another permitted app is open. Its MediaSession gives the media notification and
// lock-screen controls.
//
// Audio focus: ExoPlayer requests it (handleAudioFocus) and pauses when another app starts
// playing or a call comes in, so built-in audio and an external music app never play over each
// other. Headphones or Bluetooth disconnecting pause it (handleAudioBecomingNoisy). Nothing is
// restarted after a reboot or a service restart: only a focus period that has just begun starts
// it by itself (AudioRules), and nothing about playback is saved.
package app.lockdin.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import app.lockdin.R
import app.lockdin.state.ActiveSession

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object StudyPlayer {
    private const val TAG = "LockdInAudio"
    private const val PREVIEW_MS = 15_000L
    private const val FADE_MS = 2_500L

    // ---- What the screens show (main thread) ----
    var isPlaying by mutableStateOf(false)
        private set
    var trackTitle by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf<AudioCollection?>(null)
        private set
    /** Volume set during this session (null = the profile's). */
    var sessionVolume by mutableFloatStateOf(-1f)
        private set
    var previewing by mutableStateOf<AudioCollection?>(null)
        private set

    /** FocusService refreshes its notification when playback changes. */
    var onChange: (() -> Unit)? = null

    private var player: ExoPlayer? = null
    var mediaSession: MediaSession? = null
        private set
    private val main = Handler(Looper.getMainLooper())

    // Session bookkeeping (memory only, on purpose: a restart never resumes by itself).
    private var sessionId: String? = null
    private val autoStarted = mutableSetOf<Int>()
    private var wasPlayingAtBreak = false
    private var lastPhase = -1
    private var fadeToken = 0

    private fun resId(res: String): Int = when (res) {
        "piano_1" -> R.raw.piano_1
        "piano_2" -> R.raw.piano_2
        "piano_3" -> R.raw.piano_3
        "strings_1" -> R.raw.strings_1
        "strings_2" -> R.raw.strings_2
        "strings_3" -> R.raw.strings_3
        else -> R.raw.rain_1
    }

    private fun ensure(ctx: Context): ExoPlayer {
        player?.let { return it }
        val app = ctx.applicationContext
        val p = ExoPlayer.Builder(app)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; onChange?.invoke() }
            override fun onMediaMetadataChanged(m: MediaMetadata) { trackTitle = m.title?.toString(); onChange?.invoke() }
        })
        player = p
        mediaSession = MediaSession.Builder(app, p).setId("lockdin-study").build()
        return p
    }

    private fun load(ctx: Context, c: AudioCollection) {
        val p = ensure(ctx)
        if (loaded == c && p.mediaItemCount > 0) return
        p.setMediaItems(c.tracks.map { t ->
            MediaItem.Builder()
                .setUri(RawResourceDataSource.buildRawResourceUri(resId(t.res)))
                .setMediaId(t.res)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist("Lockd In · ${c.label}").setIsPlayable(true).build())
                .build()
        })
        p.repeatMode = if (c.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        p.prepare()
        loaded = c
        trackTitle = c.tracks.first().title
    }

    private fun volumeFor(a: ProfileAudio) = if (sessionVolume >= 0f) sessionVolume else a.volume

    // ---- Called by FocusService on every tick and phase change ----

    /**
     * Follows the session. `focusStart` is the start of the current focus period (null in a
     * break or when nothing restricts). Never starts audio for a focus period that began long ago.
     */
    fun follow(ctx: Context, session: ActiveSession?, phaseIndex: Int?, inFocus: Boolean, inBreak: Boolean, phaseStart: Long?, now: Long) {
        if (session == null || !session.audio.builtIn) { if (sessionId != null) endSession(); return }
        if (sessionId != session.id) {
            stopPreview()
            sessionId = session.id; autoStarted.clear(); wasPlayingAtBreak = false; lastPhase = -1; sessionVolume = -1f
        }
        val a = session.audio
        val idx = phaseIndex ?: -1
        if (idx != lastPhase) {
            lastPhase = idx
            if (inBreak) {
                wasPlayingAtBreak = isPlaying
                if (a.breaks == BreakAudio.PAUSE && isPlaying) fadeOut { player?.pause() }
            }
        }
        if (inFocus && phaseStart != null && idx !in autoStarted) {
            val start = AudioRules.autoStart(a, phaseStart, now, alreadyFor = false) ||
                AudioRules.resumeAfterBreak(a, wasPlayingAtBreak, phaseStart, now)
            autoStarted += idx
            if (start) { play(ctx, a); Log.i(TAG, "auto_start phase=$idx") }
            wasPlayingAtBreak = false
        }
    }

    /** Session over (end, emergency recovery): fade out, then release everything. */
    fun endSession() {
        sessionId = null; autoStarted.clear(); wasPlayingAtBreak = false; lastPhase = -1; sessionVolume = -1f
        if (isPlaying) fadeOut { release() } else release()
    }

    // ---- The person's own controls ----

    fun play(ctx: Context, a: ProfileAudio) {
        fadeToken++
        load(ctx, a.collection)
        val p = player ?: return
        p.volume = volumeFor(a)
        p.play()
        Log.i(TAG, "play collection=${a.collection}")
    }

    fun toggle(ctx: Context, a: ProfileAudio) {
        if (isPlaying) { fadeToken++; player?.pause() } else play(ctx, a)
    }

    fun next() { player?.takeIf { it.hasNextMediaItem() || it.repeatMode == Player.REPEAT_MODE_ALL }?.seekToNextMediaItem() }

    /** True if "next" means anything for this collection (not for a single ambient loop). */
    fun hasNext(c: AudioCollection) = c.tracks.size > 1

    fun setVolume(v: Float) {
        sessionVolume = v.coerceIn(0f, 1f)
        player?.volume = sessionVolume
    }

    /** Switch collection during a session (keeps playing if it was). */
    fun switchTo(ctx: Context, a: ProfileAudio) {
        val was = isPlaying
        loaded = null
        load(ctx, a.collection)
        if (was) play(ctx, a)
    }

    /** A short sample outside sessions (profile editor, setup). Never during a session. */
    fun preview(ctx: Context, c: AudioCollection, volume: Float) {
        if (sessionId != null) return
        previewing = c
        loaded = null
        load(ctx, c)
        player?.let { it.volume = volume; it.play() }
        val token = ++fadeToken
        main.postDelayed({ if (token == fadeToken && previewing != null) fadeOut { release() } }, PREVIEW_MS)
    }

    fun stopPreview() {
        if (previewing == null) return
        fadeToken++
        release()
    }

    private fun fadeOut(then: () -> Unit) {
        val p = player ?: return then()
        val token = ++fadeToken
        val from = p.volume
        val steps = 25
        for (i in 1..steps) main.postDelayed({
            if (token != fadeToken) return@postDelayed
            player?.volume = from * (1f - i / steps.toFloat())
            if (i == steps) then()
        }, FADE_MS * i / steps)
    }

    private fun release() {
        mediaSession?.release(); mediaSession = null
        player?.release(); player = null
        isPlaying = false; loaded = null; trackTitle = null; previewing = null
        onChange?.invoke()
    }
}
