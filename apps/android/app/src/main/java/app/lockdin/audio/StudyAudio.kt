// Study audio during focus (roadmap step 3, B2).
//
// Ambient sound: played by Lockd In itself (AudioTrack, generated samples), from the blocking
// service so it keeps going with the screen off. It follows the session: on in focus, paused
// for breaks unless the profile keeps it, stopped at the end and on emergency recovery.
//
// Music: NOT played by Lockd In. The playlist opens in the person's own music app (for a start
// made in the app), and the controls send Android's standard media keys and change media
// volume — no extra permission, no browsing.
package app.lockdin.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lockdin.state.ActiveSession
import app.lockdin.state.ProfileAudio

object StudyAudio {
    private const val TAG = "LockdInAudio"

    /** What the screen shows. Main thread. */
    var playing by mutableStateOf(false)
        private set
    /** The person paused ambient sound for this session (it then stays paused). */
    var pausedFor by mutableStateOf<String?>(null)
        private set
    /** The person started ambient sound for this session although auto-play is off. */
    var startedFor by mutableStateOf<String?>(null)
        private set
    /** Session-only level override (null = the profile's level). */
    var level by mutableFloatStateOf(-1f)
        private set

    private var player: AmbientPlayer? = null
    private var previewUntil = 0L
    private val main = Handler(Looper.getMainLooper())

    /** Called by the blocking service on every tick and phase change. `session` null = stop. */
    fun follow(ctx: Context, session: ActiveSession?, inFocus: Boolean, inBreak: Boolean) {
        if (SystemClock.elapsedRealtime() < previewUntil) return
        val want = session != null && AudioPlan.ambientWanted(session.audio, session.id, inFocus, inBreak, pausedFor, startedFor)
        if (want) play(ctx, session!!.audio, mixWithMusic = session.audio.musicLink != null) else stopAmbient()
        if (session == null) { pausedFor = null; startedFor = null; level = -1f }
    }

    private fun play(ctx: Context, audio: ProfileAudio, mixWithMusic: Boolean) {
        val lv = if (level >= 0f) level else audio.level
        val p = player
        if (p != null && p.kind == audio.ambient) { p.setLevel(lv); return }
        p?.stop()
        player = AmbientPlayer(ctx.applicationContext, audio.ambient, lv, requestFocus = !mixWithMusic) { playing = it }.also { it.start() }
        Log.i(TAG, "ambient_on kind=${audio.ambient} mix=$mixWithMusic")
    }

    private fun stopAmbient() {
        player?.let { it.stop(); Log.i(TAG, "ambient_off") }
        player = null
        playing = false
    }

    /** Play/pause button on the session screen. */
    fun toggle(ctx: Context, session: ActiveSession) {
        if (playing) { pausedFor = session.id; startedFor = null }
        else { pausedFor = null; startedFor = session.id }
        app.lockdin.enforce.FocusService.sync(ctx)
        // Apply at once rather than waiting for the next tick.
        val snap = app.lockdin.enforce.FocusService.evaluate(session, System.currentTimeMillis())
        val inFocus = snap.restricting || snap.phase?.kind == app.lockdin.engine.PhaseKind.FOCUS
        follow(ctx, session, inFocus, snap.phase?.kind == app.lockdin.engine.PhaseKind.BREAK)
    }

    fun setSessionLevel(v: Float) {
        level = v.coerceIn(0f, 1f)
        player?.setLevel(level)
    }

    /** Ten seconds of a sound from the profile editor (outside sessions only). */
    fun preview(ctx: Context, kind: Ambient, lv: Float) {
        stopAmbient()
        if (kind == Ambient.NONE) return
        previewUntil = SystemClock.elapsedRealtime() + 10_000
        player = AmbientPlayer(ctx.applicationContext, kind, lv, requestFocus = true) { playing = it }.also { it.start() }
        main.postDelayed({ if (SystemClock.elapsedRealtime() >= previewUntil - 50) { previewUntil = 0; stopAmbient() } }, 10_000)
    }

    fun stopPreview() { previewUntil = 0; stopAmbient() }

    // ---------------- Music (external app) ----------------

    /**
     * For a start made in the app: opens the playlist in its app, then brings Lockd In back.
     * Never used for scheduled starts (they must not bring apps onto the screen).
     */
    fun openPlaylist(ctx: Context, session: ActiveSession) {
        val link = session.audio.musicLink ?: return
        if (!session.audio.autoPlay) return
        val provider = session.audio.musicProvider
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provider?.let { p -> if (installed(ctx, p.pkg)) view.setPackage(p.pkg) }
        try {
            ctx.startActivity(view)
            Log.i(TAG, "playlist_opened provider=${provider?.name}")
            main.postDelayed({
                runCatching {
                    ctx.startActivity(Intent(ctx, app.lockdin.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
            }, 2_500)
        } catch (e: Exception) {
            Log.w(TAG, "playlist_open_failed: ${e.javaClass.simpleName}")
        }
    }

    fun installed(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    /** Play/pause or next in whatever app is playing (normally the playlist's app). */
    fun mediaKey(ctx: Context, code: Int) {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun mediaVolume(ctx: Context): Float {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setMediaVolume(ctx: Context, v: Float) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (v.coerceIn(0f, 1f) * max).toInt(), 0) }
    }
}

/**
 * Plays one generated ambient sound on its own thread with short fades. With `requestFocus`,
 * it takes audio focus and pauses while another app (a call, a video) has it; without (mixing
 * with the person's music) it still goes quiet during calls.
 */
class AmbientPlayer(
    private val ctx: Context, val kind: Ambient, level: Float, private val requestFocus: Boolean,
    private val onPlaying: (Boolean) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var target = level
    @Volatile private var focusLost = false
    private var thread: Thread? = null
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { f -> focusLost = f < 0 }.build()
    private val main = Handler(Looper.getMainLooper())

    fun setLevel(v: Float) { target = v.coerceIn(0f, 1f) }

    fun start() {
        if (running) return
        running = true
        if (requestFocus) focusLost = am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        main.post { onPlaying(true) }
        thread = Thread({ loop() }, "lockdin-ambient").apply { priority = Thread.NORM_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(800)
        thread = null
        if (requestFocus) am.abandonAudioFocusRequest(focus)
        main.post { onPlaying(false) }
    }

    private fun loop() {
        val synth = AmbientSynth(kind, seed = System.nanoTime())
        val rate = synth.sampleRate
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder().setAudioAttributes(attrs)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(min, 8192)).build()
        } catch (e: Exception) { running = false; main.post { onPlaying(false) }; return }
        val buf = ShortArray(1024)
        var gain = 0f
        track.play()
        try {
            while (running) {
                val inCall = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
                val want = if (focusLost || inCall) 0f else target
                synth.fill(buf)
                // ~0.6 s fade toward the wanted level: no clicks when starting, pausing or changing level.
                val step = buf.size.toFloat() / (rate * 0.6f)
                gain = if (gain < want) minOf(want, gain + step) else maxOf(want, gain - step)
                track.setVolume(gain * gain) // perceptual curve
                track.write(buf, 0, buf.size)
            }
            // Fade out before stopping.
            var g = gain
            while (g > 0.01f) { g *= 0.85f; track.setVolume(g * g); synth.fill(buf); track.write(buf, 0, buf.size) }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
